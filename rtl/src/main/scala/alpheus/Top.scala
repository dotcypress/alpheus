package alpheus

import chisel3._
import chisel3.util._

class Top extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val tx = Output(Bool())
    val led = Output(Bool())
    val active = Output(Bool())
    val trigger = Input(Bool())
    val clockin = Input(Bool())
    val clockout = Output(Bool())
    val glitch = Output(Bool())
  })

  val pll = Module(new PLLBlackBox())
  pll.io.REFERENCECLK := clock
  pll.io.RESET := ~reset.asBool()

  val glitcher = Module(new Glitcher(16000000, 115200, 16, 8))
  glitcher.io.rx := io.rx
  glitcher.io.fastClock := pll.io.PLLOUTCORE
  glitcher.io.clockin := io.clockin
  glitcher.io.trigger := io.trigger

  io.tx := glitcher.io.tx
  io.led := glitcher.io.led
  io.active := glitcher.io.active
  io.glitch := glitcher.io.glitch
  io.clockout := glitcher.io.clockout
}
