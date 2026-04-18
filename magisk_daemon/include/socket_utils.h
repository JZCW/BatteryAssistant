#ifndef SOCKET_UTILS_H
#define SOCKET_UTILS_H

#include <string>
#include <sys/socket.h>
#include <sys/un.h>
#include <cstring>
#include <cstddef>

inline bool buildAbstractSockaddr(const std::string& name, sockaddr_un& addr, socklen_t& addrLen) {
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // sun_path[0] 用于 abstract namespace 前导 '\0'，其余字节全部可用于名称。
    const size_t maxAbstractNameLen = sizeof(addr.sun_path) - 1;
    if (name.size() > maxAbstractNameLen) {
        return false;
    }

    addr.sun_path[0] = '\0';
    const size_t nameLen = name.size();
    memcpy(&addr.sun_path[1], name.data(), nameLen);
    addrLen = static_cast<socklen_t>(offsetof(struct sockaddr_un, sun_path) + 1 + nameLen);
    return true;
}

#endif // SOCKET_UTILS_H