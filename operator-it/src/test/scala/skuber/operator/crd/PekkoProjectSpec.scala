package skuber.operator.crd

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import skuber.api.client.{EventType, WatchEvent}
import skuber.model.ListResource

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Pekko-based integration test for Project.
 */
class PekkoProjectSpec extends ProjectSpec with PekkoK8SFixture {

  import Project.given

  it should "watch Project resources" in {
    val watchTestResourceName = s"watch-project-${java.util.UUID.randomUUID().toString.take(8)}"
    val testResource = Project(
      watchTestResourceName,
      Project.Spec(
        name = "watcher",
        owner = Project.Owner("bob", "infra"),
        repositories = List(Project.Repo("https://example.com/watch.git", "scala")),
        labels = Map("env" -> "test"),
        settings = Project.Settings(
          retentionDays = 7,
          features = Set("metrics"),
          limits = Project.Limits(cpu = 1, memoryMi = 512)
        ),
        alerts = None
      )
    )

    val trackedEvents = ListBuffer.empty[WatchEvent[Project]]
    val trackEvents: Sink[WatchEvent[Project], ?] = Sink.foreach { event =>
      trackedEvents += event
    }

    withPekkoK8sClient({ k8s =>
      def getCurrentResourceVersion: Future[String] = k8s.list[ListResource[Project]]().map { l =>
        l.resourceVersion
      }

      def watchAndTrackEvents(sinceVersion: String) = {
        k8s
          .getWatcher[Project]
          .watchStartingFromVersion(sinceVersion)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(trackEvents)(Keep.both).run()
      }

      val newStatus = Project.Status(
        phase = "Ready",
        conditions = List(Project.Condition("Healthy", "True", None)),
        metrics = Map("latencyMs" -> 12.5, "errorRate" -> 0.01),
        lastDeploy = Some(Project.DeployInfo("v1.2.3", "2025-01-10T12:34:56Z")),
        members = Vector(Project.Member("carol", "maintainer"))
      )

      val killSwitchFut: Future[UniqueKillSwitch] = for {
        currentResourceVersion <- getCurrentResourceVersion
        (kill, _) = watchAndTrackEvents(currentResourceVersion)
        created <- k8s.create(testResource)
        _ <- k8s.updateStatus(created.copy(status = Some(newStatus)))
        _ <- k8s.delete[Project](watchTestResourceName)
      } yield kill

      Await.ready(killSwitchFut, 60.seconds)

      eventually(timeout(30.seconds), interval(1.second)) {
        trackedEvents.size shouldBe 3
        trackedEvents.head._type shouldBe EventType.ADDED
        trackedEvents.head._object.name shouldBe watchTestResourceName
        trackedEvents.head._object.spec.owner.name shouldBe "bob"
        trackedEvents(1)._type shouldBe EventType.MODIFIED
        trackedEvents(1)._object.status.get.phase shouldBe "Ready"
        trackedEvents(1)._object.status.get.metrics("latencyMs") shouldBe 12.5
        trackedEvents(2)._type shouldBe EventType.DELETED
      }

      // cleanup
      killSwitchFut.map { killSwitch =>
        killSwitch.shutdown()
        succeed
      }
    }, 120.seconds)
  }
}
