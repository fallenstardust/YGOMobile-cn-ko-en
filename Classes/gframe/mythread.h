#ifndef THREAD_H
#define THREAD_H

#ifdef _WIN32

#include <windows.h>

class Thread {
public:
	static void NewThread(int (*thread_func)(void*), void* param) {
		CreateThread(0, 0, (LPTHREAD_START_ROUTINE)thread_func, param, 0, 0);
	}
};

#else // _WIN32

#include <pthread.h>
#ifdef __IRR_ANDROID_PLATFORM_
#include <sys/resource.h>
#endif

class Thread {
public:
	static void NewThread(int (*thread_func)(void*), void* param) {
		pthread_t thread;
		pthread_attr_t attr;
		pthread_attr_init(&attr);
		pthread_create(&thread, &attr, (void * (*)(void *))thread_func, param);
#ifdef __IRR_ANDROID_PLATFORM_
		//10 for THREAD_PRIORITY_BACKGROUND
		setpriority(PRIO_PROCESS, tid, 10);
#endif
	}
};

#endif // _WIN32

#endif // THREAD_H
