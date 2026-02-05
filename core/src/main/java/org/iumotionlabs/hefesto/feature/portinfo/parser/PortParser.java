package org.iumotionlabs.hefesto.feature.portinfo.parser;

import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;

import java.util.List;

/**
 * Interface for parsing port information from OS commands.
 * Sealed to control platform-specific implementations.
 */
public sealed interface PortParser
    permits LinuxPortParser, MacOsPortParser, WindowsPortParser {

    /**
     * Finds bindings for a specific port.
     *
     * @param port the port number to search
     * @param tcp  include TCP connections
     * @param udp  include UDP connections
     * @return list of port bindings
     */
    List<PortBinding> findByPort(int port, boolean tcp, boolean udp);

    /**
     * Finds all ports associated with a process.
     *
     * @param pid the process ID
     * @return list of port bindings
     */
    List<PortBinding> findByPid(long pid);

    /**
     * Finds ports in a range.
     *
     * @param from       start of range (inclusive)
     * @param to         end of range (inclusive)
     * @param listenOnly only return ports in LISTEN state
     * @return list of port bindings
     */
    List<PortBinding> findInRange(int from, int to, boolean listenOnly);

    /**
     * Finds all ports currently in LISTEN state.
     *
     * @return list of all listening port bindings
     */
    List<PortBinding> findAllListening();

    /**
     * Finds all active port bindings (any state).
     *
     * @param tcp include TCP connections
     * @param udp include UDP connections
     * @return list of all port bindings
     */
    List<PortBinding> findAll(boolean tcp, boolean udp);

    /**
     * Finds ports by process name (case-insensitive partial match).
     *
     * @param processName the process name to search for
     * @return list of port bindings for matching processes
     */
    List<PortBinding> findByProcessName(String processName);

    /**
     * Terminates a process.
     *
     * @param pid   the process ID to kill
     * @param force use SIGKILL instead of SIGTERM
     * @return true if the process was terminated
     */
    boolean killProcess(long pid, boolean force);

    /**
     * Finds by port with TCP only (convenience).
     */
    default List<PortBinding> findByPort(int port) {
        return findByPort(port, true, false);
    }

    /**
     * Finds all TCP and UDP bindings (convenience).
     */
    default List<PortBinding> findAll() {
        return findAll(true, true);
    }
}
