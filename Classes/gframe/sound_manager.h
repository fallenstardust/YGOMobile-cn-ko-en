#ifndef SOUNDMANAGER_H
#define SOUNDMANAGER_H

#include <random>
#include "sound_openal.h"
#include "utils.h"

namespace ygo {

class SoundManager {
public:
	enum SFX {
		SUMMON,
		SPECIAL_SUMMON,
		ACTIVATE,
		SET,
		FLIP,
		REVEAL,
		EQUIP,
		DESTROYED,
		BANISHED,
		TOKEN,
		ATTACK,
		DIRECT_ATTACK,
		DRAW,
		SHUFFLE,
		DAMAGE,
		RECOVER,
		COUNTER_ADD,
		COUNTER_REMOVE,
		COIN,
		DICE,
		NEXT_TURN,
		PHASE,
		BUTTON,
		INFO,
		QUESTION,
		CARD_PICK,
		CARD_DROP,
		PLAYER_ENTER,
		CHAT
	};
    enum BGM {
        ALL,
        DUEL,
        MENU,
        DECK,
        ADVANTAGE,
        DISADVANTAGE,
        WIN,
        LOSE
    };
	bool Init(double sounds_volume, double music_volume, bool sounds_enabled, bool music_enabled, void* payload = nullptr);
	void RefreshBGMList();
	void PlaySoundEffect(SFX sound);
	void PlayDialogSound(irr::gui::IGUIElement * element);
	void PlayMusic(const std::string& song, bool loop);
	void PlayBGM(BGM scene);
	void StopSound();
	void StopBGM();
	bool PlayChant(unsigned int code);
	void PlayCustomSound(char* SoundName);
	void PlayCustomBGM(char* BGMName);
	void SetSoundVolume(double volume);
	void SetMusicVolume(double volume);
	void EnableSounds(bool enable);
	void EnableMusic(bool enable);

private:
    std::vector<std::string> BGMList[8];
    std::map<unsigned int, std::string> ChantsList;
    int bgm_scene = -1;
	bool bgm_process;
    std::mt19937 rnd;
    std::unique_ptr<YGOpen::OpenALSingleton> openal;
    std::unique_ptr<YGOpen::OpenALSoundLayer> sfx;
    std::unique_ptr<YGOpen::OpenALSoundLayer> bgm;
    int bgmCurrent = -1;
    void RefreshBGMDir(path_string path, BGM scene);
    void RefreshChantsList();
    bool soundsEnabled = false;
    bool musicEnabled = false;
};

}

#endif //SOUNDMANAGER_H