package alpheus

import chisel3._
import chisel3.iotesters.PeekPokeTester

class SlotTester(dut: Slot) extends PeekPokeTester(dut) {
  poke(dut.io.delay, 3)
  poke(dut.io.pulse, 2)
  poke(dut.io.load, true)

  step(1)
  poke(dut.io.load, false)

  step(1)
  poke(dut.io.enable, true)
  expect(dut.io.powerGlitch, false)
  expect(dut.io.clockGlitch, false)

  step(1)
  expect(dut.io.powerGlitch, false)

  step(3)
  expect(dut.io.powerGlitch, true)
  expect(dut.io.clockGlitch, true)

  step(10)
  expect(dut.io.powerGlitch, false)
  poke(dut.io.enable, false)

  step(5)
  poke(dut.io.enable, true)
  step(10)
}

object OneShotTester extends App {
  iotesters.Driver.execute(
    Array("--target-dir", "build", "--generate-vcd-output", "on"),
    () => new Slot(8)
  ) { c => new SlotTester(c) }
}
