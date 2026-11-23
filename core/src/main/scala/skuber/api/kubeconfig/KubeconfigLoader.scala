package skuber.api.kubeconfig

import java.io.InputStream

import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._

import scala.util.Try

/**
 * Loads a kubeconfig YAML document into the [[RawConfig]] model.
 *
 * Parsing pipeline: SnakeYAML (already a skuber dependency) decodes the YAML into a plain
 * `java.util.Map`/`List`/`String`/`Number`/`Boolean`/`Date` object graph, Jackson's `ObjectMapper`
 * serializes that graph to a JSON string (it natively understands all of those Java types - no
 * custom (de)serializers are needed), and Play JSON parses that string and decodes it via the
 * typed `Reads` in [[KubeconfigModel]].
 *
 * `java.util.Date` (which SnakeYAML produces for unquoted YAML timestamps, e.g. a legacy
 * `auth-provider: gcp` config's unquoted `expiry:` field) is deliberately left to Jackson's default
 * serialization as a JSON number (epoch millis) rather than attempting to match Jackson's own
 * default ISO-ish date-string format against a `DateTimeFormatter` later - epoch millis is
 * unambiguous. See `KubeconfigInstant.parseLenient`, which handles both that and quoted ISO-8601
 * date strings (which SnakeYAML leaves as plain `String`s).
 */
private[kubeconfig] object KubeconfigLoader {

  private val mapper = new ObjectMapper()

  def load(is: InputStream): Try[RawConfig] = Try {
    val yamlObject = new Yaml().load[Object](is)
    val jsonString = mapper.writeValueAsString(yamlObject)
    Json.parse(jsonString).validate[RawConfig] match {
      case JsSuccess(config, _) => config
      case error: JsError => throw new RuntimeException(s"failed to parse kubeconfig: ${JsError.toJson(error)}")
    }
  }
}
