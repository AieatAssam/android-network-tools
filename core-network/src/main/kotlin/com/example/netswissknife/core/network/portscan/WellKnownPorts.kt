package com.example.netswissknife.core.network.portscan

/** Metadata about a well-known port. */
data class PortInfo(
    val serviceName: String,
    val description: String,
    val protocol: String = "TCP"
)

/**
 * Registry of well-known TCP ports with service name and description.
 * Used for service identification after a port is found open.
 */
object WellKnownPorts {

    private val registry: Map<Int, PortInfo> = mapOf(
        // System / Infrastructure
        20   to PortInfo("FTP-DATA",  "FTP data transfer"),
        21   to PortInfo("FTP",       "File Transfer Protocol"),
        22   to PortInfo("SSH",       "Secure Shell"),
        23   to PortInfo("TELNET",    "Telnet – unencrypted remote shell"),
        25   to PortInfo("SMTP",      "Simple Mail Transfer Protocol"),
        53   to PortInfo("DNS",       "Domain Name System"),
        67   to PortInfo("DHCP",      "DHCP server", "UDP"),
        68   to PortInfo("DHCP",      "DHCP client", "UDP"),
        69   to PortInfo("TFTP",      "Trivial File Transfer Protocol", "UDP"),
        80   to PortInfo("HTTP",      "Hypertext Transfer Protocol"),
        88   to PortInfo("Kerberos",  "Kerberos authentication"),
        110  to PortInfo("POP3",      "Post Office Protocol v3"),
        119  to PortInfo("NNTP",      "Network News Transfer Protocol"),
        123  to PortInfo("NTP",       "Network Time Protocol", "UDP"),
        135  to PortInfo("MSRPC",     "Microsoft RPC endpoint mapper"),
        137  to PortInfo("NetBIOS-NS","NetBIOS Name Service", "UDP"),
        139  to PortInfo("NetBIOS",   "NetBIOS session service"),
        143  to PortInfo("IMAP",      "Internet Message Access Protocol"),
        161  to PortInfo("SNMP",      "Simple Network Management Protocol", "UDP"),
        162  to PortInfo("SNMP-TRAP", "SNMP trap receiver", "UDP"),
        179  to PortInfo("BGP",       "Border Gateway Protocol"),
        194  to PortInfo("IRC",       "Internet Relay Chat"),
        389  to PortInfo("LDAP",      "Lightweight Directory Access Protocol"),
        443  to PortInfo("HTTPS",     "HTTP over TLS/SSL"),
        445  to PortInfo("SMB",       "Server Message Block (file sharing)"),
        465  to PortInfo("SMTPS",     "SMTP over TLS"),
        514  to PortInfo("SYSLOG",    "Syslog", "UDP"),
        515  to PortInfo("LPD",       "Line Printer Daemon"),
        543  to PortInfo("Klogin",    "Kerberos login"),
        544  to PortInfo("Kshell",    "Kerberos remote shell"),
        587  to PortInfo("SMTP-SUB",  "SMTP mail submission"),
        631  to PortInfo("IPP",       "Internet Printing Protocol"),
        636  to PortInfo("LDAPS",     "LDAP over TLS/SSL"),
        666  to PortInfo("DOOM",      "DOOM multiplayer game"),
        993  to PortInfo("IMAPS",     "IMAP over TLS/SSL"),
        995  to PortInfo("POP3S",     "POP3 over TLS/SSL"),

        // Remote Access / VPN
        1194 to PortInfo("OpenVPN",   "OpenVPN"),
        1433 to PortInfo("MSSQL",     "Microsoft SQL Server"),
        1434 to PortInfo("MSSQL-BR",  "Microsoft SQL Server Browser", "UDP"),
        1521 to PortInfo("OracleDB",  "Oracle Database"),
        1723 to PortInfo("PPTP",      "Point-to-Point Tunneling Protocol"),
        1900 to PortInfo("UPNP",      "Universal Plug and Play", "UDP"),
        2049 to PortInfo("NFS",       "Network File System"),
        2082 to PortInfo("cPanel",    "cPanel web hosting control panel"),
        2083 to PortInfo("cPanel-S",  "cPanel over TLS"),
        2181 to PortInfo("ZooKeeper", "Apache ZooKeeper"),
        2375 to PortInfo("Docker",    "Docker daemon API (unencrypted)"),
        2376 to PortInfo("Docker-S",  "Docker daemon API (TLS)"),
        2483 to PortInfo("OracleDB-S","Oracle DB TLS listener"),
        3000 to PortInfo("HTTP-Dev",  "Development web server (Node/Rails)"),
        3306 to PortInfo("MySQL",     "MySQL / MariaDB database"),
        3389 to PortInfo("RDP",       "Windows Remote Desktop Protocol"),
        3690 to PortInfo("SVN",       "Subversion version control"),
        4000 to PortInfo("HTTP-Dev",  "Development web server"),
        4444 to PortInfo("Metasploit","Metasploit reverse shell (default)"),
        4848 to PortInfo("GlassFish", "GlassFish admin console"),
        5000 to PortInfo("UPnP/Dev",  "UPnP or development server"),
        5432 to PortInfo("PostgreSQL","PostgreSQL database"),
        5433 to PortInfo("PostgreSQL","PostgreSQL (alternate)"),
        5601 to PortInfo("Kibana",    "Kibana log visualization"),
        5672 to PortInfo("AMQP",      "RabbitMQ AMQP"),
        5900 to PortInfo("VNC",       "Virtual Network Computing"),
        5901 to PortInfo("VNC-1",     "VNC display :1"),
        6379 to PortInfo("Redis",     "Redis in-memory data store"),
        6443 to PortInfo("k8s-API",   "Kubernetes API server"),
        7001 to PortInfo("WebLogic",  "Oracle WebLogic Server"),
        8000 to PortInfo("HTTP-Alt",  "HTTP alternate port"),
        8080 to PortInfo("HTTP-Proxy","HTTP proxy / alternate web"),
        8081 to PortInfo("HTTP-Alt2", "HTTP alternate port 2"),
        8443 to PortInfo("HTTPS-Alt", "HTTPS alternate port"),
        8888 to PortInfo("Jupyter",   "Jupyter Notebook server"),
        9000 to PortInfo("SonarQube", "SonarQube / PHP-FPM"),
        9090 to PortInfo("Prometheus","Prometheus monitoring"),
        9092 to PortInfo("Kafka",     "Apache Kafka broker"),
        9200 to PortInfo("Elastic",   "Elasticsearch REST API"),
        9300 to PortInfo("Elastic-N", "Elasticsearch node-to-node"),
        9418 to PortInfo("Git",       "Git protocol"),
        10000 to PortInfo("Webmin",   "Webmin admin panel"),
        11211 to PortInfo("Memcached","Memcached in-memory cache"),
        15672 to PortInfo("RabbitMQ", "RabbitMQ management UI"),
        25565 to PortInfo("Minecraft","Minecraft game server"),
        27017 to PortInfo("MongoDB",  "MongoDB document database"),
        27018 to PortInfo("MongoDB-S","MongoDB shard server"),
        28015 to PortInfo("RethinkDB","RethinkDB database"),
        50000 to PortInfo("Jenkins",  "Jenkins CI server"),
        54321 to PortInfo("PostgreSQL","PostgreSQL (alternate)"),
    )

    /** Returns the [PortInfo] for a given port number, or null if unknown. */
    fun getInfo(port: Int): PortInfo? = registry[port]

    /** Returns the service name for a port, or a generic label. */
    fun getServiceName(port: Int): String =
        registry[port]?.serviceName ?: when (port) {
            in 1..1023   -> "Well-known"
            in 1024..49151 -> "Registered"
            else           -> "Dynamic/Private"
        }

    /** Common port presets for quick scanning. */
    val COMMON_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 110, 143, 443, 465, 587,
        993, 995, 3306, 3389, 5432, 5900, 6379, 8080, 8443,
        8888, 9200, 27017
    )

    val WEB_PORTS = listOf(80, 443, 8000, 8080, 8081, 8443, 8888, 3000, 4000, 5000)

    val DATABASE_PORTS = listOf(1433, 1521, 3306, 5432, 5433, 6379, 9200, 9300, 11211, 27017, 27018, 28015)

    val MAIL_PORTS = listOf(25, 110, 143, 465, 587, 993, 995)

    val REMOTE_ACCESS_PORTS = listOf(22, 23, 3389, 5900, 5901, 1194, 1723)
}
