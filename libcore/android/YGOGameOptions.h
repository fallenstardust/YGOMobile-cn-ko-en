/*
 * YGOGameOptions.h
 *
 *  Created on: 2014-4-5
 *      Author: mabin
 */

#ifndef YGOGAMEOPTIONS_H_
#define YGOGAMEOPTIONS_H_

#include "bufferio_android.h"
#include "../gframe/config.h"
#include "xstring.h"

namespace irr {
namespace android {

class YGOGameOptions {
public:
	YGOGameOptions(void* data);
	virtual ~YGOGameOptions();

	inline const char* getIPAddr() const {
		return m_pipAddr;
	}

	inline const char* getUserName() {
		return m_puserName == NULL ? "MyCard" : m_puserName;
	}

	inline int getPort() const {
		return m_port;
	}

	inline bool formatGameParams(wchar_t* dst) {
		if (m_proomName == NULL && m_phostInfo == NULL) {
			return false;
		}
		if (m_phostInfo != NULL) {
			BufferIO::DecodeUTF8(m_phostInfo, dst);
			return true;
		}
		char formatParams[512] = { 0 };
		if (m_isCompleteOptions) {
			sprintf(formatParams, "%d%d%c%c%c%d,%d,%d,%s", m_rule, m_mode,
					m_enablePriority, m_noDeckCheck, m_noDeckShuffle, m_startLP,
					m_startHand, m_drawCount, m_proomName);

		} else {
			if (m_mode == 0) {
				sprintf(formatParams, "%s", m_proomName);
			} else {
				sprintf(formatParams, "%c#%s", m_mode == 1 ? 'M' : 'T', m_proomName);
			}

		}
		if (m_proomPasswd != NULL) {
			char * extraParams = formatParams + strlen(formatParams);
			sprintf(extraParams, "$%s", m_proomPasswd);
		}
		BufferIO::DecodeUTF8(formatParams, dst);
		return true;
	}
private:
	char* m_pipAddr;
	char* m_puserName;
	char* m_proomName;
	char* m_proomPasswd;
	char* m_phostInfo;
	int m_port;
	int m_mode;
	bool m_isCompleteOptions;
	int m_rule;
	int m_startLP;
	int m_startHand;
	int m_drawCount;
	char m_enablePriority;
	char m_noDeckCheck;
	char m_noDeckShuffle;
};

} /* namespace android */
}
/* namespace irr */
#endif /* YGOGAMEOPTIONS_H_ */
