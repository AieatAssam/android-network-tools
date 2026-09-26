package net.aieat.netswissknife.core.network.topology

import org.snmp4j.PDU
import org.snmp4j.Session
import org.snmp4j.Target
import org.snmp4j.TransportMapping
import org.snmp4j.event.ResponseEvent
import org.snmp4j.event.ResponseListener
import org.snmp4j.smi.Address

/** Tracks only one TreeUtils walk's requests so cancellation leaves the shared SNMP client usable. */
internal class CancellableSnmpSession(
    private val delegate: Session,
) : Session by delegate {
    private class PendingRequest(
        val pdu: PDU,
        val listener: ResponseListener,
    )

    private val lock = Any()
    private val pendingRequests = mutableSetOf<PendingRequest>()
    private var cancelled = false

    override fun <A : Address> send(
        pdu: PDU,
        target: Target<A>,
        userHandle: Any?,
        listener: ResponseListener,
    ) {
        sendTracked(pdu, listener) { tracked -> delegate.send(pdu, target, userHandle, tracked) }
    }

    override fun <A : Address> send(
        pdu: PDU,
        target: Target<A>,
        transport: TransportMapping<in A>,
        userHandle: Any?,
        listener: ResponseListener,
    ) {
        sendTracked(pdu, listener) { tracked -> delegate.send(pdu, target, transport, userHandle, tracked) }
    }

    fun cancelPendingRequests() {
        val toCancel = synchronized(lock) {
            if (cancelled) return
            cancelled = true
            pendingRequests.toList().also { pendingRequests.clear() }
        }
        toCancel.forEach { request -> delegate.cancel(request.pdu, request.listener) }
    }

    private fun sendTracked(
        pdu: PDU,
        originalListener: ResponseListener,
        send: (ResponseListener) -> Unit,
    ) {
        lateinit var pending: PendingRequest
        val trackedListener = object : ResponseListener {
            override fun <A : Address> onResponse(event: ResponseEvent<A>) {
                synchronized(lock) { pendingRequests.remove(pending) }
                originalListener.onResponse(event)
            }
        }
        pending = PendingRequest(pdu, trackedListener)

        synchronized(lock) {
            if (cancelled) return
            pendingRequests += pending
            try {
                send(trackedListener)
            } catch (failure: Throwable) {
                pendingRequests.remove(pending)
                throw failure
            }
        }
    }
}
