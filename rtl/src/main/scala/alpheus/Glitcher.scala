package alpheus

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class GlitcherIO extends Bundle {
  val rx = Input(UInt(1.W))
  val tx = Output(UInt(1.W))
  val led = Output(Bool())
  val clockin = Input(Bool())
  val clockout = Output(Bool())
  val active = Output(Bool())
  val glitch = Output(Bool())
  val trigger = Input(Bool())
  val fastClock = Input(Clock())
}

object Glitcher {
  object State extends ChiselEnum {
    val Setup, WaitTrigger, Glitch, Done, Error = Value
  }

  def TAG_SOFT_RESET = 0.U
  def TAG_DONE = 1.U
  def TAG_ERROR = 2.U
  def TAG_UPDATE_CLOCK = 3.U
  def TAG_UPDATE_TRIGGER = 4.U
  def TAG_UPDATE_SLOT = 5.U
  def TAG_START = 6.U

  def TRIGGER_NONE = 0.U
  def TRIGGER_EXT = 1.U
  def TRIGGER_EXT_INV = 2.U

  def CLOCK_SRC_INT = 0.U
  def CLOCK_SRC_EXT = 1.U
}

class Glitcher(clockFreq: Int, baudRate: Int, slotLen: Int, slotSize: Int)
    extends Module {
  require(
    slotLen > 0 && slotLen < 64,
    "Invalid slot amount"
  )
  import Glitcher.State

  val io = IO(new GlitcherIO)

  val state = RegInit(State.Setup)
  io.led := state === State.WaitTrigger || state === State.Glitch

  val trigger = RegInit(Glitcher.TRIGGER_NONE)
  val clockSource = RegInit(Glitcher.CLOCK_SRC_INT)
  val prescaler = RegInit(0.U)
  
  val intClock = RegInit(false.B)
  val counter = RegInit(0.U(8.W))
  
  when(counter >= prescaler) {
    counter := 0.U
    intClock := ~intClock
  }.otherwise {
    counter := counter + 1.U
  }
  
  val mtlv = Module(new MTLV(clockFreq, baudRate, 4))
  mtlv.io.out.ready := true.B
  mtlv.io.in.bits.tag := 0.U
  mtlv.io.in.bits.len := 0.U
  mtlv.io.in.bits.value := 0.U
  mtlv.io.in.valid := false.B
  mtlv.io.rx := io.rx
  io.tx := mtlv.io.tx

  val packet = mtlv.io.out.bits

  withClock(io.fastClock) {
    val slots = Array.fill(slotLen)(Module(new Slot(slotSize)).io)

    for (slotIndex <- 0 until slotLen) {
      val updateSlot =
        state === State.Setup && mtlv.io.out.valid && packet.tag === Glitcher.TAG_UPDATE_SLOT && (packet
          .value(
            8,
            0
          ) === slotIndex.U)

      slots(slotIndex).load := updateSlot
      slots(slotIndex).enable := state === State.Glitch
      slots(slotIndex).delay := packet.value(24, 8)
      slots(slotIndex).pulse := packet.value(40, 24)
    }

    val done = slots.map(slot => slot.done).reduce(_ && _)
    val glitch = slots.map(slot => slot.glitch).reduce(_ || _)
    val clockout = Mux(
      clockSource === Glitcher.CLOCK_SRC_EXT,
      io.clockin,
      intClock
    )

    io.active := state === State.Glitch
    io.glitch := glitch
    io.clockout := clockout ^ glitch

    when(state === State.Glitch && done) {
      state := State.Done
    }.elsewhen(
      state === State.WaitTrigger && trigger === Glitcher.TRIGGER_EXT && io.trigger
    ) {
      state := State.Glitch
    }.elsewhen(
      state === State.WaitTrigger && trigger === Glitcher.TRIGGER_EXT_INV && ~io.trigger
    ) {
      state := State.Glitch
    }
  }

  when(mtlv.io.out.valid) {
    switch(packet.tag) {
      is(Glitcher.TAG_SOFT_RESET) {
        trigger := Glitcher.TRIGGER_NONE
        clockSource := Glitcher.CLOCK_SRC_INT
        prescaler := 0.U
        intClock := false.B
        counter := 0.U(8.W)
        state := State.Done
      }
      is(Glitcher.TAG_START) {
        state := Mux(
          state === State.Setup,
          Mux(
            trigger === Glitcher.TRIGGER_NONE,
            State.Glitch,
            State.WaitTrigger
          ),
          State.Error
        )
      }
      is(Glitcher.TAG_UPDATE_CLOCK) {
        when(state === State.Setup) {
          clockSource := packet.value(8, 0)
          prescaler := packet.value(16, 8)
          state := State.Done
        } otherwise {
          state := State.Error
        }
      }
      is(Glitcher.TAG_UPDATE_TRIGGER) {
        when(state === State.Setup) {
          trigger := packet.value(8, 0)
          state := State.Done
        } otherwise {
          state := State.Error
        }
      }
      is(Glitcher.TAG_UPDATE_SLOT) {
        when(state === State.Setup) {
          state := State.Done
        }.otherwise {
          state := State.Error
        }
      }
    }
  }

  when(mtlv.io.in.ready) {
    switch(state) {
      is(State.Done) {
        state := State.Setup
        mtlv.io.in.bits.tag := Glitcher.TAG_DONE
        mtlv.io.in.valid := true.B
      }
      is(State.Error) {
        state := State.Setup
        mtlv.io.in.bits.tag := Glitcher.TAG_ERROR
        mtlv.io.in.valid := true.B
      }
    }
  }
}
