package com.yasliks.hiddify_library_lib.core

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import com.hiddify.core.libbox.*
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import java.net.InetAddress
import java.net.InetSocketAddress

class HiddifyPlatform internal constructor(
    private val context: Context,
    private val service: VpnService? = null,
    isEnabledApps: Boolean = false,
    appsList: List<String> = emptyList(),
    private val probeNetwork: Network? = null,
) : PlatformInterface {
    constructor(
        service: VpnService,
        isEnabledApps: Boolean = false,
        appsList: List<String> = emptyList(),
    ) : this(
        context = service,
        service = service,
        isEnabledApps = isEnabledApps,
        appsList = appsList,
    )

    private val sdk get() = EasyHiddify.instance
    @Volatile
    private var appSelection = AppSelection(
        isEnabled = isEnabledApps,
        apps = appsList,
    )

    private data class AppSelection(
        val isEnabled: Boolean,
        val apps: List<String>,
    )

    internal fun updateApps(
        isEnabled: Boolean,
        apps: List<String>,
    ) {
        appSelection = AppSelection(
            isEnabled = isEnabled,
            apps = apps,
        )
    }

    private class StringArrayIterator(private val items: List<String>) : StringIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < items.size
        override fun len(): Int = items.size
        override fun next(): String = items[index++]
    }

    override fun openTun(options: TunOptions): Int {
        val service = checkNotNull(service) { "TUN is unavailable for URL tests" }
        val (isEnabledApps, appsList) = appSelection
        val customMtu = sdk.settings.mtu.value
        val mtu = if (customMtu > 0) customMtu else HiddifyPrefs.BASE_MTU

        sdk.logger.append(2, "[PLATFORM] openTun called from core. MTU: $mtu, AutoRoute: ${options.autoRoute}")

        val builder = service.Builder()
        if (!isEnabledApps || appsList.isEmpty()) {
            try {
                // Exclude the application itself (and all its processes, including :vpn) from the tunnel
                builder.addDisallowedApplication(service.packageName)
                sdk.logger.append(2, "[PLATFORM] Disallowed self package from VPN: ${service.packageName}")
            } catch (e: Exception) {
                val err = service.getString(R.string.failed_to_disallow, e.message ?: "")
                sdk.logger.append(3, "[PLATFORM WARNING] $err")
            }
        }

        builder.setSession(HiddifyPrefs.HIDDIFY_SMALL)
        builder.setMtu(mtu)

        // Setting up IP addresses
        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val addr = inet4.next()
            builder.addAddress(addr.address(), addr.prefix())
            sdk.logger.append(2, "[PLATFORM] IPv4 added: ${addr.address()}/${addr.prefix()}")
        }

        val isIpv6Enabled = sdk.settings.enableIPv6.value
        if (isIpv6Enabled) {
            val inet6 = options.inet6Address
            while (inet6.hasNext()) {
                val addr = inet6.next()
                builder.addAddress(addr.address(), addr.prefix())
                sdk.logger.append(2, "[PLATFORM] IPv6 added: ${addr.address()}/${addr.prefix()}")
            }
        }

        // DNS Configuration
        val userDns = sdk.settings.dnsServer.value
        val defaultDns = if (userDns.isNotEmpty()) userDns else HiddifyPrefs.BASE_DNS_SERVER

        val rawDns = try {
            val dnsIter = options.dnsServerAddress
            if (dnsIter != null && dnsIter.hasNext()) dnsIter.next() else null
        } catch (_: Exception) { null }

        val parsedDnsAddress = rawDns
            ?.replace("tcp://", "")
            ?.replace("udp://", "")
            ?.replace("https://", "")
            ?.replace("http://", "")
            ?.substringBefore("/")
            ?.substringBefore(":")
            ?.takeIf { it.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) }
            ?: defaultDns

        builder.addDnsServer(parsedDnsAddress)

        if (options.autoRoute) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else {
                    builder.addRoute(HiddifyPrefs.BASE_ROUTE, 0)
                }

                // IPv6 routing only if enabled
                if (isIpv6Enabled) {
                    val inet6RouteAddress = options.inet6RouteAddress
                    if (inet6RouteAddress.hasNext()) {
                        while (inet6RouteAddress.hasNext()) {
                            builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                        }
                    } else {
                        builder.addRoute(HiddifyPrefs.ZERO_ROUTE, 0)
                    }
                }
            } else {
                builder.addRoute(HiddifyPrefs.BASE_ROUTE, 0)
                if (isIpv6Enabled) builder.addRoute(HiddifyPrefs.ZERO_ROUTE, 0)
            }

            sdk.logger.append(2, "[PLATFORM] isEnabledApps = $isEnabledApps, appsList == $appsList")
            builder.enabledApps(isEnabledApps = isEnabledApps, listApps = appsList)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val pfd = builder.establish() ?: run {
            val err = service.getString(R.string.vpn_prepare_required)
            sdk.logger.append(4, "[PLATFORM ERROR] $err")
            error(err)
        }

        sdk.logger.append(2, "[PLATFORM] VPN Tunnel established successfully. FD: ${pfd.fd}")
        return pfd.fd
    }

    /**
     * Extends VPN operation to a list of tunneling applications
     *
     * @param listApps list of tunneling applications
     * @param isEnabledApps enable the list of tunneling applications
     */
    private fun VpnService.Builder.enabledApps(
        isEnabledApps: Boolean = true,
        listApps: List<String>,
    ): VpnService.Builder {
        if (!isEnabledApps || listApps.isEmpty()) {
            sdk.logger.append(2, "[PLATFORM] Split tunneling disabled or empty app list")
            return this
        }
        sdk.logger.append(2, "[PLATFORM] Configuring split tunneling for ${listApps.size} apps...")

        val addedApps = HashSet<String>()
        for (app in listApps) {
            if (app.isBlank() || app in addedApps || app == context.packageName) continue
            if (appIsExists(app)) {
                try {
                    this.addAllowedApplication(app)
                    addedApps.add(app)
                } catch (e: Exception) {
                    // The package may be deleted in the process or may not be suitable.
                    sdk.logger.append(3, "[PLATFORM WARNING] Failed to add app $app: ${e.message}")
                }
            }
        }
        return this
    }

    /**
     * Checks if the app is installed on the device
     *
     * @param packageName Package Name
     * @return true - if the application is installed, false - in all other cases.
     */
    private fun appIsExists(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            // The app isn't installed.
            return false
        }
    }

    override fun sendNotification(notification: Notification) {
        if (service == null) return
        sdk.state.updateNotification(notification)
        val systemNotification = sdk.notifications.createNotification(serverName = null, notification = notification)
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(HiddifyPrefs.NOTIFICATION_ID, systemNotification)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RoutePrefix.toIpPrefix() = IpPrefix(InetAddress.getByName(address()), prefix())

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val uid = manager.getConnectionOwnerUid(
                /* protocol = */ ipProtocol,
                /* local = */ InetSocketAddress(sourceAddress, sourcePort),
                /* remote = */ InetSocketAddress(destinationAddress, destinationPort),
            )

            val owner = ConnectionOwner()
            owner.userId = uid
            if (uid != Process.INVALID_UID) {
                val packages = context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
                owner.userName = packages.firstOrNull() ?: ""
                owner.setAndroidPackageNames(StringArrayIterator(packages))
            }
            owner
        } catch (e: Exception) {
            ConnectionOwner()
        }
    }

    override fun readWIFIState(): WIFIState = WIFIState("", "")
    override fun clearDNSCache() {}
    override fun getInterfaces(): NetworkInterfaceIterator? = null
    override fun includeAllNetworks(): Boolean = true
    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun cancelNotification(identifier: String?, typeID: Int) {}
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun systemCertificates(): StringIterator? = null
    override fun underNetworkExtension(): Boolean = false
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun autoDetectInterfaceControl(fd: Int) {
        if (probeNetwork != null) {
            ParcelFileDescriptor.fromFd(fd).use { probeNetwork.bindSocket(it.fileDescriptor) }
        } else if (service?.protect(fd) == false) {
            Log.i("HiddifyPlatform", "Failed to protect socket fd: $fd")
        }
    }
    override fun checkPlatformShell() {}
    override fun createAutoRedirect(options: ByteArray?, handler: AutoRedirectHandler?): AutoRedirectSession = throw Exception("AutoRedirect is not supported")
    override fun createBridge(options: BridgeOptions?): BridgeSession = throw Exception("Bridge is not supported")
    override fun lookupSFTPServer(): String = throw Exception("SFTP server is not supported")
    override fun lookupUser(username: String?): PlatformUser = throw Exception("lookupUser is not supported")
    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int): ShellSession = throw Exception("ShellSession is not supported")
    override fun readSystemSSHHostKey(): String = throw Exception("SSH host key is not supported")
    override fun registerMyInterface(name: String?) {}
    override fun tailscaleHostname(): String = "Android"
    override fun usePlatformAutoRedirect(): Boolean = false
    override fun usePlatformBridge(): Boolean = false
    override fun usePlatformShell(): Boolean = false
}
