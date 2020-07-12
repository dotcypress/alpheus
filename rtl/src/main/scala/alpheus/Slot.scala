package alpheus

import chisel3._
import chisel3.util._;
import chisel3.experimental.ChiselEnum;

class SlotIO(slotWidth: Int) extends Bundle {
  val load = Input(Bool())
  val enable = Input(Bool())
  val delay = Input(UInt(slotWidth.W))
  val pulse = Input(UInt(slotWidth.W))

  val glitch = Output(Bool())
  val done = Output(Bool())

  override def cloneType = (new SlotIO(slotWidth)).asInstanceOf[this.type]
}

object SlotState extends ChiselEnum {
  val Ready, Delay, Glitch, Done = Value
}

class Slot(slotWidth: Int) extends Module {
  val io = IO(new SlotIO(slotWidth))

  val pulse = RegInit(0.U(slotWidth.W))
  val delay = RegInit(0.U(slotWidth.W))

  val state = RegInit(SlotState.Ready)
  val counter = RegInit(0.U((slotWidth).W))

  io.glitch := state === SlotState.Glitch
  io.done := state === SlotState.Done

  def waitBeforeTransition(cycles: UInt, nextState: SlotState.Type) = {
    val succ = counter + 1.U;
    when(~io.enable) {
      state := SlotState.Ready
    }.elsewhen(succ >= cycles) {
      state := nextState
      counter := 0.U
    }.otherwise {
      counter := succ
    }
  }

  switch(state) {
    is(SlotState.Ready) {
      when(io.load) {
        delay := io.delay
        pulse := io.pulse
      }.elsewhen(io.enable) {
        counter := 0.U
        state := Mux(
          pulse === 0.U,
          SlotState.Done,
          Mux(delay === 0.U, SlotState.Glitch, SlotState.Delay)
        )
      }
    }
    is(SlotState.Delay) {
      waitBeforeTransition(delay, SlotState.Glitch)
    }
    is(SlotState.Glitch) {
      waitBeforeTransition(pulse, SlotState.Done)
    }
    is(SlotState.Done) {
      when(~io.enable) {
        state := SlotState.Ready
      }
    }
  }
}
