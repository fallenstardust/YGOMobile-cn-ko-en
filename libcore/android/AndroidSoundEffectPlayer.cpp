/*
 * AndroidSoundEffectPlayer.cpp
 *
 *  Created on: 2014-10-1
 *      Author: mabin
 */

#include "AndroidSoundEffectPlayer.h"
#include "OpenSLSoundTracker.h"
#include "SoundPoolWrapperTracker.h"

namespace ygo {

AndroidSoundEffectPlayer::AndroidSoundEffectPlayer(ANDROID_APP app) :
		m_pAudioTracker(NULL), m_isEnabled(false) {
	if (m_pAudioTracker == NULL) {
//		m_pAudioTracker = new OpenSLSoundTracker(app->activity->assetManager);
		m_pAudioTracker = new SoundPoolWrapperTracker(app);
	}
}

AndroidSoundEffectPlayer::~AndroidSoundEffectPlayer() {
	if (m_pAudioTracker != NULL) {
		delete m_pAudioTracker;
		m_pAudioTracker = NULL;
	}
}

void AndroidSoundEffectPlayer::setSEEnabled(bool enabled) {
	m_isEnabled = enabled;
}

void AndroidSoundEffectPlayer::doPlayerEnterEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/playerenter.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doShuffleCardEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/shuffle.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doNewTurnEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/nextturn.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doNewPhaseEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/phase.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doSetCardEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/set.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doSummonEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/summon.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doSpecialSummonEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/specialsummon.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doFlipCardEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/flip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doActivateEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/activate.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDrawCardEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/draw.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDamageEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/damage.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doGainLpEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/addcounter.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doEquipEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/equip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doAddCounterEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/addcounter.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doRemoveCounterEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/coinflip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doAttackEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/attack.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doCoinFlipEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/coinflip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDiceRollEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/diceroll.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doChatEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/chatmessage.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDestroyEffect() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/destroyed.ogg"),
				AP_TYPE_ASSET);
	}
}

//add new soundeffect and BGM
void AndroidSoundEffectPlayer::doMenuBGM() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/MenuBGM.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDeckBgm() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/DeckBgm.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doSaveDeck() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/addcounter.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDelete() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/phase.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doLeave() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/equip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doReady() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/addcounte.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doUnReady() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/phase.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doPlayerExit() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/equip.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doOpeningBGM() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doOpeningBGM.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doDisadvantageBgm() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doDisadvantageBgm.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doAdvantageBgm() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doAdvantageBgm.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doWinBgm() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doWinBgm.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doLoseBgm() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doLoseBgm.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doStartGame() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/doStartGame.ogg"),
				AP_TYPE_ASSET);
	}
}

void AndroidSoundEffectPlayer::doPressButton() {
	if (m_isEnabled) {
		m_pAudioTracker->playBGM(irr::io::path("sound/chatmessage.ogg"),
				AP_TYPE_ASSET);
	}
}

} /* namespace ygo */
