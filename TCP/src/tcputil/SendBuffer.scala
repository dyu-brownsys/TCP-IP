package tcputil

import tcp.TCPConnection
import java.util.concurrent.Semaphore

class SendBuffer(capacity: Int, conn: TCPConnection) {
  var writeBuf: Array[Byte] = new Array[Byte](0)
  var sendBuf: Array[Byte] = new Array[Byte](0)

  var available: Int = capacity
  var slide: Int = capacity

  val semaphoreCheckAvailalbe = new Semaphore(0)

  var busy: Boolean = false

  def write(buf: Array[Byte]): Int = {
    var realLen: Int = 0
    this.synchronized {
      if (available == 0) {
        this.wait
      }
      realLen = math.min(buf.length, available)
      writeBuf ++= buf.slice(0, realLen)
      available -= realLen
    }

    // notify the data sending
    conn.wakeUpDataSend

    realLen
  }

  def read(size: Int): Array[Byte] = {
    this.synchronized {
      // maybe slide < sendBuf.length
      if (slide <= sendBuf.length) {
        if (slide == 0) {
          if (writeBuf.length != 0 && sendBuf.length == 0) {
            val pending = writeBuf.slice(0, 1)
            sendBuf ++= pending

            pending
          } else {
            new Array[Byte](0)
          }
        } else {
          new Array[Byte](0)
        }
      } else {
        val realLen = math.min(math.min(size, writeBuf.length), slide - sendBuf.length)
        val pending = writeBuf.slice(0, realLen)
        writeBuf = writeBuf.slice(realLen, writeBuf.length)
        sendBuf ++= pending

        pending
      }
    }
  }

  def fastRetransmit(mss: Int): Array[Byte] = {
    val dstFlowWindowSize = conn.getFlowWindow
    this.synchronized {
      if (sendBuf.length != 0) {
        if (slide == 0) {
          sendBuf.slice(0, 1)
        } else {
          val realLen = math.min(math.min(mss, sendBuf.length), slide)
          sendBuf.slice(0, realLen)
        }
      } else {
        new Array[Byte](0)
      }
    }
  }

  def retransmit(): Array[Byte] = {
    this.synchronized {
      if (slide == 0) {
        if (sendBuf.length != 0) {
          // probe
          sendBuf.slice(0, 1)
        } else {
          new Array[Byte](0)
        }
      } else {
        sendBuf.slice(0, slide)
      }
    }
  }

  def removeFlightData(len: Int) {
    this.synchronized {
      if (len != 0) {
        if (available == 0) {
          this.notify
        }
        if (len <= sendBuf.length) {
          sendBuf = sendBuf.slice(len, sendBuf.length)
          available += len
        } else {
          // need to remove bytes more than sent bytes
          available += sendBuf.length
          sendBuf = new Array[Byte](0)
        }
        if (available == capacity && busy) {
          this.semaphoreCheckAvailalbe.release
        }
      }
    }

    // notify the data sending
    conn.wakeUpDataSend
  }

  def getSendLength(): Int = {
    this.synchronized {
      sendBuf.length
    }
  }

  def setSliding(newSliding: Int) {
    this.synchronized {
      slide = newSliding
    }
  }

  def getSliding(): Int = {
    this.synchronized {
      slide
    }
  }

  def waitAvailable() {
    this.synchronized {
      if (available == capacity) {
        return
      }
      this.busy = true
    }
    this.semaphoreCheckAvailalbe.acquire
  }

  def isEmpty(): Boolean = {
    this.synchronized {
      available == capacity
    }
  }
}