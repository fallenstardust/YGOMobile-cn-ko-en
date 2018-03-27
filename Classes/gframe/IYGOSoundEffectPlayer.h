/*
 * IYGOSoundEffectPlayer.h
 *
 *  Created on: 2014-9-30
 *      Author: mabin
 */

#ifndef IYGOSOUNDEFFECTPLAYER_H_
#define IYGOSOUNDEFFECTPLAYER_H_

namespace ygo {

class IYGOSoundEffectPlayer {
public:
	IYGOSoundEffectPlayer(){}

	virtual ~IYGOSoundEffectPlayer(){}

	virtual void doPlayerEnterEffect() = 0;

	virtual void doShuffleCardEffect() = 0;

	virtual void doNewTurnEffect() = 0;

	virtual void doNewPhaseEffect() = 0;

	virtual void doSetCardEffect() = 0;

	virtual void doSummonEffect() = 0;

	virtual void doSpecialSummonEffect() = 0;

	virtual void doFlipCardEffect() = 0;

	virtual void doActivateEffect() = 0;

	virtual void doDrawCardEffect() = 0;

	virtual void doDamageEffect() = 0;

	virtual void doGainLpEffect() = 0;

	virtual void doEquipEffect() = 0;

	virtual void doAddCounterEffect() = 0;

	virtual void doRemoveCounterEffect() = 0;

	virtual void doAttackEffect() = 0;

	virtual void doCoinFlipEffect() = 0;

	virtual void doDiceRollEffect() = 0;

	virtual void doChatEffect() = 0;

	virtual void doDestroyEffect() = 0;

	virtual void setSEEnabled(bool enabled) = 0;

	//add new sound effect and BGM @fallenstardust
	virtual void doMenuBGM() = 0;
	
	virtual void doDeckBgm() = 0;
	
	virtual void doSaveDeck() = 0;
	
	virtual void doDelete() = 0;
	
	virtual void doLeave() = 0;
	
	virtual void doReady() = 0;
	
	virtual void doUnReady() = 0;
	
	virtual void doPlayerExit() = 0;
	
	virtual void doOpeningBGM() = 0;
	
	virtual void doDisadvantageBgm() = 0;
	
	virtual void doAdvantageBgm() = 0;
	
	virtual void doWinBgm() = 0;
	
	virtual void doLoseBgm() = 0;
	
	virtual void doStartGame() = 0;
	
	virtual void doPressButton() = 0;

	
};

}

#endif /* IYGOSOUNDEFFECTPLAYER_H_ */
