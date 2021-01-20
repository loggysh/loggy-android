package loggy.sh

import sh.loggy.Application
import sh.loggy.Device

interface LoggyContext {
    fun getApplication(): Application
    fun getDevice(): Device
}