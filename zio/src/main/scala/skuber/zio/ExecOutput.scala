package skuber.zio

enum ExecOutput:
  case Stdout(data: String)
  case Stderr(data: String)
