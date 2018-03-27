/*
 * YGOGameOptions.cpp
 *
 *  Created on: 2014-4-5
 *      Author: mabin
 */

#include "YGOGameOptions.h"
#include "string.h"
#include "stdio.h"
#include "../gframe/os.h"

namespace irr {
namespace android {

YGOGameOptions::YGOGameOptions(void* data): m_pipAddr(NULL), m_puserName(NULL), m_proomName(NULL), m_proomPasswd(NULL),
		m_phostInfo(NULL){
	//read ip addr
	char log[128];
	char * rawdata = (char*)data;
	int tmplength = ::BufferIO::ReadInt32(rawdata);
	if (tmplength != 0) {
		m_pipAddr = new char[tmplength + 1];
		memset(m_pipAddr, 0, tmplength + 1);
		memcpy(m_pipAddr, rawdata, tmplength);
		rawdata += tmplength;
	}

	//read user name
	tmplength = ::BufferIO::ReadInt32(rawdata);
	if (tmplength != 0) {
		m_puserName = new char[tmplength + 1];
		memset(m_puserName, 0, tmplength + 1);
		memcpy(m_puserName, rawdata, tmplength);
		rawdata += tmplength;
	}

	//read room name
	tmplength = ::BufferIO::ReadInt32(rawdata);
	if (tmplength != 0) {
		m_proomName = new char[tmplength + 1];
		memset(m_proomName, 0, tmplength + 1);
		memcpy(m_proomName, rawdata, tmplength);
		rawdata += tmplength;
	}

	//read room password
	tmplength = ::BufferIO::ReadInt32(rawdata);
	if (tmplength != 0) {
		m_proomPasswd = new char[tmplength];
		memcpy(m_proomPasswd, rawdata, tmplength);
		rawdata += tmplength;
	}
	//read host info
	tmplength = ::BufferIO::ReadInt32(rawdata);
	if (tmplength != 0) {
		m_phostInfo = new char[tmplength + 1];
		memset(m_phostInfo, 0, tmplength + 1);
		memcpy(m_phostInfo, rawdata, tmplength);
		rawdata += tmplength;
	}

	m_port = ::BufferIO::ReadInt32(rawdata);
	m_mode = ::BufferIO::ReadInt32(rawdata);
	m_isCompleteOptions = ::BufferIO::ReadInt32(rawdata) == 1;

	if (m_isCompleteOptions) {
		m_rule = ::BufferIO::ReadInt32(rawdata);
		m_startLP = ::BufferIO::ReadInt32(rawdata);
		m_startHand = ::BufferIO::ReadInt32(rawdata);
		m_drawCount = ::BufferIO::ReadInt32(rawdata);

		m_enablePriority = ::BufferIO::ReadInt32(rawdata) == 1 ? 'T' : 'F';
		m_noDeckCheck = ::BufferIO::ReadInt32(rawdata) == 1 ? 'T' : 'F';
		m_noDeckShuffle = ::BufferIO::ReadInt32(rawdata) == 1 ? 'T' : 'F';
	}
}

YGOGameOptions::~YGOGameOptions() {
	if (m_pipAddr != NULL) {
		delete m_pipAddr;
	}
	if (m_puserName != NULL) {
		delete m_puserName;
	}
	if (m_proomName != NULL) {
		delete m_proomName;
	}
	if (m_proomPasswd != NULL) {
		delete m_proomPasswd;
	}
}

} /* namespace android */
} /* namespace irr */
