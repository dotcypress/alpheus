const SerialPort = require('serialport')
const MTLVTransform = require('mtlv/transform')

const TAG_RESET = 0
const TAG_DONE = 1
const TAG_ERROR = 2
const TAG_UPDATE_CLOCK = 3
const TAG_UPDATE_TRIGGER = 4
const TAG_UPDATE_SLOT = 5
const TAG_START = 6

class Alpheus {
  constructor(portName, packetCb = () => { }) {
    this.port = new SerialPort(portName, { baudRate: 115200, autoOpen: true, endOnClose: true })
    this.link = this.port.pipe(new MTLVTransform())
    this.link.on('data', packetCb)
    this.link.on('error', packetCb)
  }

  disconnect() {
    this.port.close()
  }

  reset() {
    this.link.decoder.reset()
    return this.sendTag(TAG_RESET)
  }

  start() {
    return this.sendTag(TAG_START)
  }

  updateClock(clockSource, prescaler) {
    return this.sendTag(TAG_UPDATE_CLOCK, [clockSource, prescaler])
  }

  updateTrigger(triggerConfig) {
    return this.sendTag(TAG_UPDATE_TRIGGER, [triggerConfig])
  }

  updateSlot(slot, delay, pulse) {
    const delayLow = delay & 0xff
    const delayHigh = (delay >> 8) & 0xff
    const pulseLow = pulse & 0xff
    const pulseHigh = (pulse >> 8) & 0xff
    return this.sendTag(TAG_UPDATE_SLOT, [slot, delayLow, delayHigh, pulseLow, pulseHigh])
  }

  sendTag(command, args = []) {
    const packet = this.link.encoder.encode(command, args)
    return new Promise((resolve, reject) => {
      this.port.write(packet, (err) => {
        if (err) {
          return reject(err)
        }
        this.link.once('data', (answer) => {
          if (answer.tag === TAG_DONE) {
            resolve()
          } else {
            reject(answer)
          }
        })
      })
    })
  }
}

module.exports = {
  Alpheus,
  TAG_ERROR,
  TAG_DONE,
}