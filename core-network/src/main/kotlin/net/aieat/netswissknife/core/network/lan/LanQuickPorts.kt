package net.aieat.netswissknife.core.network.lan

/**
 * TCP ports probed on every responsive host, in probe order. The app derives its port labels and
 * actions from this list.
 */
object LanQuickPorts {
    val ports: List<Int> =
        listOf(
            21,
            22,
            23,
            25,
            53,
            80,
            110,
            139,
            143,
            443,
            445,
            3306,
            3389,
            5900,
            8080,
            8443,
        )
}
