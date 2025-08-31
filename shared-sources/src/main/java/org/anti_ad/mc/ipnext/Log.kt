/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2022 Plamen K. Kosseff <p.kosseff@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.anti_ad.mc.ipnext

import org.anti_ad.mc.common.LogBase

object Log: LogBase() {

    override val id: String
        get() = "Inventory Profiles Next"

    class TimedMessage(val message: String,
                       val start: Long,
                       val indent: Int,
                       var end: Long,
                       val children: MutableList<TimedMessage> = mutableListOf())
    @PublishedApi
    internal val indent = ThreadLocal.withInitial { 0 }
    @PublishedApi
    internal val parentMessage = ThreadLocal<TimedMessage?>()


    inline fun <T> traceTimed(message: String, action: () -> T): T {
        return if (shouldTrace()) {

            val start = System.nanoTime()
            val offset = indent.get()
            val indentStr = "  ".repeat(offset)
            val tmsg = TimedMessage(message, start, offset, 0L)
            val parent = parentMessage.get()
            parent?.children?.add(tmsg)
            parentMessage.set(tmsg)

            indent.set(offset + 1)
            //trace { "${indentStr}$message... {" }
            try {
                action()
            } finally {
                indent.set(offset)
                tmsg.end = System.nanoTime()
                parentMessage.set(parent)
                if (parent == null) {
                    traceTimedTree(tmsg)
                }
                //trace { "${indentStr}} ($message). ${(System.nanoTime() - start) / 1000000} ms" }
            }
        } else {
            action()
        }
    }

    fun traceTimedTree(message: TimedMessage) {
        val indentStr = "  ".repeat(message.indent)
        if (message.children.isEmpty()) {
            trace { "${indentStr}${message.message} (${(message.end - message.start) / 1000000F} ms)" }
        } else {
            trace { "${indentStr}${message.message} {" }
            for (child in message.children) {
                traceTimedTree(child)
            }
            trace { "${indentStr}} (${(message.end - message.start) / 1000000F} ms)" }
        }
    }
}
