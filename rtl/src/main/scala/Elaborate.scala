package build

object Elaborate extends App {
  chisel3.Driver.execute(
    Array("--target-dir", "build"),
    () => new alpheus.Top()
  )
}
