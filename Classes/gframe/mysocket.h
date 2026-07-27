#ifndef YGOPRO_SOCKET_H
#define YGOPRO_SOCKET_H

#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>

namespace ygo {



using Socket = int;
constexpr Socket INVALID_SOCKET_HANDLE = -1;
constexpr int SOCKET_RESULT_ERROR = -1;

inline int CloseSocket(Socket socket) {
	return close(socket);
}

}

#endif // YGOPRO_SOCKET_H
