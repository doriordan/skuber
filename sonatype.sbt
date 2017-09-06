sonatypeProfileName := "io.github.doriordan"

publishMavenStyle := true

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage := Some(url("https://github.com/doriordan/skuber"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/doriordan/skuber"),
    "scm:git@github.com:doriordan/skuber.git"
  )
)
developers := List(
  Developer(id="doriordan", name="David O'Riordan", email="doriordan@gmail.com", url=url("https://github.com/doriordan"))
)
