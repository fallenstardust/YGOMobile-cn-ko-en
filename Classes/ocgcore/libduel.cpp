/*
 * libduel.cpp
 *
 *  Created on: 2010-5-6
 *      Author: Argon
 */

#include "scriptlib.h"
#include "duel.h"
#include "field.h"
#include "card.h"
#include "effect.h"
#include "group.h"
#include "ocgapi.h"

#ifdef _IRR_ANDROID_PLATFORM_
int32_t scriptlib::duel_load_script(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_STRING, 1);
	duel* pduel = interpreter::get_duel_info(L); 
	const char* pstr = lua_tostring(L, 1);
	char filename[64];
	sprintf(filename, "./script/%s", pstr);
	lua_pushboolean(L, pduel->lua->load_script(filename));
	return 1;
}
#endif

int32_t scriptlib::duel_enable_global_flag(lua_State *L) {
	check_param_count(L, 1);
	int32_t flag = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.global_flag |= flag;
	return 0;
}

int32_t scriptlib::duel_get_lp(lua_State *L) {
	check_param_count(L, 1);
	int32_t p = (int32_t)lua_tointeger(L, 1);
	if(p != 0 && p != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->player[p].lp);
	return 1;
}
int32_t scriptlib::duel_set_lp(lua_State *L) {
	check_param_count(L, 2);
	int32_t p = (int32_t)lua_tointeger(L, 1);
	int32_t lp = (int32_t)lua_tonumber(L, 2);
	if(lp < 0) lp = 0;
	if(p != 0 && p != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->player[p].lp = lp;
	pduel->write_buffer8(MSG_LPUPDATE);
	pduel->write_buffer8(p);
	pduel->write_buffer32(lp);
	return 0;
}
int32_t scriptlib::duel_is_turn_player(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->infos.turn_player == playerid)
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_get_turn_player(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->infos.turn_player);
	return 1;
}
int32_t scriptlib::duel_get_turn_count(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) > 0) {
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		if(playerid != 0 && playerid != 1)
			return 0;
		lua_pushinteger(L, pduel->game_field->infos.turn_id_by_player[playerid]);
	} else
		lua_pushinteger(L, pduel->game_field->infos.turn_id);
	return 1;
}
int32_t scriptlib::duel_get_draw_count(lua_State *L) {
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	lua_pushinteger(L, pduel->game_field->get_draw_count(playerid));
	return 1;
}
int32_t scriptlib::duel_register_effect(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_EFFECT, 1);
	effect* peffect = *(effect**)lua_touserdata(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = peffect->pduel;
	pduel->game_field->add_effect(peffect, playerid);
	return 0;
}
int32_t scriptlib::duel_register_flag_effect(lua_State *L) {
	check_param_count(L, 5);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t code = (lua_tointeger(L, 2) & MAX_CARD_ID) | EFFECT_FLAG_EFFECT;
	int32_t reset = (int32_t)lua_tointeger(L, 3);
	uint64_t flag = lua_tointeger(L, 4);
	int32_t count = (int32_t)lua_tointeger(L, 5);
	lua_Integer lab = 0;
	if(lua_gettop(L) >= 6)
		lab = lua_tointeger(L, 6);
	if(count == 0)
		count = 1;
	if(reset & (RESET_PHASE) && !(reset & (RESET_SELF_TURN | RESET_OPPO_TURN)))
		reset |= (RESET_SELF_TURN | RESET_OPPO_TURN);
	duel* pduel = interpreter::get_duel_info(L);
	effect* peffect = pduel->new_effect();
	peffect->effect_owner = playerid;
	peffect->owner = pduel->game_field->temp_card;
	peffect->handler = 0;
	peffect->type = EFFECT_TYPE_FIELD;
	peffect->code = code;
	peffect->reset_flag = reset;
	peffect->flag[0] = flag | EFFECT_FLAG_CANNOT_DISABLE | EFFECT_FLAG_PLAYER_TARGET | EFFECT_FLAG_FIELD_ONLY;
	peffect->s_range = 1;
	peffect->o_range = 0;
	peffect->reset_count = count;
	peffect->label.push_back(lab);
	pduel->game_field->add_effect(peffect, playerid);
	interpreter::effect2value(L, peffect);
	return 1;
}
int32_t scriptlib::duel_get_flag_effect(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t code = (lua_tointeger(L, 2) & MAX_CARD_ID) | EFFECT_FLAG_EFFECT;
	duel* pduel = interpreter::get_duel_info(L);
	effect_set eset;
	pduel->game_field->filter_player_effect(playerid, code, &eset);
	lua_pushinteger(L, eset.size());
	return 1;
}
int32_t scriptlib::duel_reset_flag_effect(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t code = (lua_tointeger(L, 2) & MAX_CARD_ID) | EFFECT_FLAG_EFFECT;
	duel* pduel = interpreter::get_duel_info(L);
	auto pr = pduel->game_field->effects.aura_effect.equal_range(code);
	for(; pr.first != pr.second; ) {
		auto rm = pr.first++;
		effect* peffect = rm->second;
		if(peffect->code == code && peffect->is_target_player(playerid))
			pduel->game_field->remove_effect(peffect);
	}
	return 0;
}
int32_t scriptlib::duel_set_flag_effect_label(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t code = (lua_tointeger(L, 2) & MAX_CARD_ID) | EFFECT_FLAG_EFFECT;
	auto lab = lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	effect_set eset;
	pduel->game_field->filter_player_effect(playerid, code, &eset);
	if(!eset.size())
		lua_pushboolean(L, FALSE);
	else {
		eset[0]->label.clear();
		eset[0]->label.push_back(lab);
		lua_pushboolean(L, TRUE);
	}
	return 1;
}
int32_t scriptlib::duel_get_flag_effect_label(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t code = (lua_tointeger(L, 2) & MAX_CARD_ID) | EFFECT_FLAG_EFFECT;
	duel* pduel = interpreter::get_duel_info(L);
	effect_set eset;
	pduel->game_field->filter_player_effect(playerid, code, &eset);
	if(!eset.size()) {
		lua_pushnil(L);
		return 1;
	}
	for(effect_set::size_type i = 0; i < eset.size(); ++i)
		lua_pushinteger(L, eset[i]->label.size() ? eset[i]->label[0] : 0);
	return (int32_t)eset.size();
}
int32_t scriptlib::duel_destroy(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t reason = (uint32_t)lua_tointeger(L, 2);
	uint32_t dest = LOCATION_GRAVE;
	if(lua_gettop(L) >= 3)
		dest = (uint32_t)lua_tointeger(L, 3);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	if (lua_gettop(L) >= 4)
		reason_player = (uint32_t)lua_tointeger(L, 4);
	if(pcard)
		pduel->game_field->destroy(pcard, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, dest, 0);
	else
		pduel->game_field->destroy(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, dest, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_remove(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t pos = (uint32_t)lua_tointeger(L, 2);
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	if (lua_gettop(L) >= 4)
		reason_player = (uint32_t)lua_tointeger(L, 4);
	if(pcard)
		pduel->game_field->send_to(pcard, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, LOCATION_REMOVED, 0, pos);
	else
		pduel->game_field->send_to(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, LOCATION_REMOVED, 0, pos);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_sendto_grave(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t reason = (uint32_t)lua_tointeger(L, 2);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	if (lua_gettop(L) >= 3)
		reason_player = (uint32_t)lua_tointeger(L, 3);
	if(pcard)
		pduel->game_field->send_to(pcard, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, LOCATION_GRAVE, 0, POS_FACEUP);
	else
		pduel->game_field->send_to(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player, PLAYER_NONE, LOCATION_GRAVE, 0, POS_FACEUP);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_summon(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 4);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	uint32_t ignore_count = lua_toboolean(L, 3);
	effect* peffect = nullptr;
	if(!lua_isnil(L, 4)) {
		check_param(L, PARAM_TYPE_EFFECT, 4);
		peffect = *(effect**)lua_touserdata(L, 4);
	}
	uint32_t min_tribute = 0;
	if(lua_gettop(L) >= 5)
		min_tribute = (uint32_t)lua_tointeger(L, 5);
	uint32_t zone = 0x1f;
	if(lua_gettop(L) >= 6)
		zone = (uint32_t)lua_tointeger(L, 6);
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->summon(playerid, pcard, peffect, ignore_count, min_tribute, zone, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->summon(playerid, pcard, peffect, ignore_count, min_tribute, zone, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_special_summon_rule(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	uint32_t sumtype = 0;
	if(lua_gettop(L) >= 3)
		sumtype = (uint32_t)lua_tointeger(L, 3);
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->special_summon_rule(playerid, pcard, sumtype, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->special_summon_rule(playerid, pcard, sumtype, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_synchro_summon(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	card* tuner = nullptr;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_CARD, 3);
		tuner = *(card**)lua_touserdata(L, 3);
	}
	group* mg = nullptr;
	if(lua_gettop(L) >= 4) {
		if(!lua_isnil(L, 4)) {
			check_param(L, PARAM_TYPE_GROUP, 4);
			group* pgroup = *(group**) lua_touserdata(L, 4);
			mg = pduel->new_group(pgroup->container);
			mg->is_readonly = GTYPE_READ_ONLY;
		}
	}
	int32_t minc = 0;
	if(lua_gettop(L) >= 5)
		minc = (int32_t)lua_tointeger(L, 5);
	int32_t maxc = 0;
	if(lua_gettop(L) >= 6)
		maxc = (int32_t)lua_tointeger(L, 6);
	pduel->game_field->core.limit_tuner = tuner;
	pduel->game_field->core.limit_syn = mg;
	pduel->game_field->core.limit_syn_minc = minc;
	pduel->game_field->core.limit_syn_maxc = maxc;
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_SYNCHRO, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_SYNCHRO, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_xyz_summon(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	group* materials = nullptr;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_GROUP, 3);
		group* pgroup = *(group**)lua_touserdata(L, 3);
		materials = pduel->new_group(pgroup->container);
		materials->is_readonly = GTYPE_READ_ONLY;
	}
	int32_t minc = 0;
	if(lua_gettop(L) >= 4)
		minc = (int32_t)lua_tointeger(L, 4);
	int32_t maxc = 0;
	if(lua_gettop(L) >= 5)
		maxc = (int32_t)lua_tointeger(L, 5);
	pduel->game_field->core.limit_xyz = materials;
	pduel->game_field->core.limit_xyz_minc = minc;
	pduel->game_field->core.limit_xyz_maxc = maxc;
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_XYZ, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_XYZ, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_link_summon(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	group* materials = nullptr;
	card* lcard = nullptr;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_GROUP, 3);
		group* pgroup = *(group**)lua_touserdata(L, 3);
		materials = pduel->new_group(pgroup->container);
		materials->is_readonly = GTYPE_READ_ONLY;
	}
	if(lua_gettop(L) >= 4) {
		if(!lua_isnil(L, 4)) {
			check_param(L, PARAM_TYPE_CARD, 4);
			lcard = *(card**)lua_touserdata(L, 4);
		}
	}
	int32_t minc = 0;
	if(lua_gettop(L) >= 5)
		minc = (int32_t)lua_tointeger(L, 5);
	int32_t maxc = 0;
	if(lua_gettop(L) >= 6)
		maxc = (int32_t)lua_tointeger(L, 6);
	pduel->game_field->core.limit_link = materials;
	pduel->game_field->core.limit_link_card = lcard;
	pduel->game_field->core.limit_link_minc = minc;
	pduel->game_field->core.limit_link_maxc = maxc;
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_LINK, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->special_summon_rule(playerid, pcard, SUMMON_TYPE_LINK, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_setm(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 4);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**)lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.effect_damage_step)
		return 0;
	uint32_t ignore_count = lua_toboolean(L, 3);
	effect* peffect = nullptr;
	if(!lua_isnil(L, 4)) {
		check_param(L, PARAM_TYPE_EFFECT, 4);
		peffect = *(effect**)lua_touserdata(L, 4);
	}
	uint32_t min_tribute = 0;
	if(lua_gettop(L) >= 5)
		min_tribute = (uint32_t)lua_tointeger(L, 5);
	uint32_t zone = 0x1f;
	if(lua_gettop(L) >= 6)
		zone = (uint32_t)lua_tointeger(L, 6);
	pduel->game_field->core.summon_cancelable = FALSE;
	if(pduel->game_field->core.current_chain.size()) {
		pduel->game_field->mset(playerid, pcard, peffect, ignore_count, min_tribute, zone, SUMMON_IN_CHAIN);
		pduel->game_field->core.summon_reserved = pduel->game_field->core.subunits.back();
		pduel->game_field->core.subunits.pop_back();
		pduel->game_field->core.summoning_card = pcard;
	}
	else {
		pduel->game_field->mset(playerid, pcard, peffect, ignore_count, min_tribute, zone, SUMMON_IN_IDLE);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_sets(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t toplayer = playerid;
	if(lua_gettop(L) > 2)
		toplayer = (uint32_t)lua_tointeger(L, 3);
	if(toplayer != 0 && toplayer != 1)
		toplayer = playerid;
	uint32_t confirm = TRUE;
	if(lua_gettop(L) > 3)
		confirm = lua_toboolean(L, 4);
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
		card* pcard = *(card**) lua_touserdata(L, 2);
		pduel = pcard->pduel;
		pgroup = pduel->new_group(pcard);
	} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 2);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 2);
	pduel->game_field->add_process(PROCESSOR_SSET_G, 0, pduel->game_field->core.reason_effect, pgroup, playerid, toplayer, confirm);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_create_token(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t code = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	card* pcard = pduel->new_card(code);
	pcard->owner = playerid;
	pcard->current.location = 0;
	pcard->current.controler = playerid;
	interpreter::card2value(L, pcard);
	return 1;
}
int32_t scriptlib::duel_special_summon(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 7);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t sumtype = (uint32_t)lua_tointeger(L, 2);
	uint32_t sumplayer = (uint32_t)lua_tointeger(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 4);
	uint32_t nocheck = lua_toboolean(L, 5);
	uint32_t nolimit = lua_toboolean(L, 6);
	uint32_t positions = (uint32_t)lua_tointeger(L, 7);
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 8)
		zone = (uint32_t)lua_tointeger(L, 8);
	if(pcard) {
		card_set cset{ pcard };
		pduel->game_field->special_summon(cset, sumtype, sumplayer, playerid, nocheck, nolimit, positions, zone);
	} else
		pduel->game_field->special_summon(pgroup->container, sumtype, sumplayer, playerid, nocheck, nolimit, positions, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_special_summon_step(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 7);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	duel* pduel = pcard->pduel;
	uint32_t sumtype = (uint32_t)lua_tointeger(L, 2);
	uint32_t sumplayer = (uint32_t)lua_tointeger(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 4);
	uint32_t nocheck = lua_toboolean(L, 5);
	uint32_t nolimit = lua_toboolean(L, 6);
	uint32_t positions = (uint32_t)lua_tointeger(L, 7);
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 8)
		zone = (uint32_t)lua_tointeger(L, 8);
	pduel->game_field->special_summon_step(pcard, sumtype, sumplayer, playerid, nocheck, nolimit, positions, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_special_summon_complete(lua_State *L) {
	check_action_permission(L);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->special_summon_complete(pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_sendto_hand(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(lua_isnil(L, 2) || (playerid != 0 && playerid != 1))
		playerid = PLAYER_NONE;
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	if (lua_gettop(L) >= 4)
		reason_player = (uint32_t)lua_tointeger(L, 4);
	if(pcard)
		pduel->game_field->send_to(pcard, pduel->game_field->core.reason_effect, reason, reason_player, playerid, LOCATION_HAND, 0, POS_FACEUP);
	else
		pduel->game_field->send_to(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player, playerid, LOCATION_HAND, 0, POS_FACEUP);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_sendto_deck(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 4);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(lua_isnil(L, 2) || (playerid != 0 && playerid != 1))
		playerid = PLAYER_NONE;
	uint32_t sequence = (uint32_t)lua_tointeger(L, 3);
	uint32_t reason = (uint32_t)lua_tointeger(L, 4);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	uint8_t send_activating = FALSE;
	if (lua_gettop(L) >= 5)
		reason_player = (uint32_t)lua_tointeger(L, 5);
	if (lua_gettop(L) >= 6)
		send_activating = (uint8_t)lua_toboolean(L, 6);
	if (pcard)
		pduel->game_field->send_to(pcard, pduel->game_field->core.reason_effect, reason, reason_player, playerid, LOCATION_DECK, sequence, POS_FACEUP, send_activating);
	else
		pduel->game_field->send_to(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player, playerid, LOCATION_DECK, sequence, POS_FACEUP, send_activating);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_sendto_extra(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(lua_isnil(L, 2) || (playerid != 0 && playerid != 1))
		playerid = PLAYER_NONE;
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	if(pcard)
		pduel->game_field->send_to(pcard, pduel->game_field->core.reason_effect, reason, pduel->game_field->core.reason_player, playerid, LOCATION_EXTRA, 0, POS_FACEUP);
	else
		pduel->game_field->send_to(pgroup->container, pduel->game_field->core.reason_effect, reason, pduel->game_field->core.reason_player, playerid, LOCATION_EXTRA, 0, POS_FACEUP);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_get_operated_group(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group(pduel->game_field->core.operated_set);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_is_can_add_counter(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_PLACE_COUNTER));
	else {
		check_param_count(L, 4);
		int32_t countertype = (int32_t)lua_tointeger(L, 2);
		int32_t count = (int32_t)lua_tointeger(L, 3);
		check_param(L, PARAM_TYPE_CARD, 4);
		card* pcard = *(card**) lua_touserdata(L, 4);
		lua_pushboolean(L, pduel->game_field->is_player_can_place_counter(playerid, pcard, countertype, count));
	}
	return 1;
}
int32_t scriptlib::duel_remove_counter(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 6);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	uint32_t countertype = (uint32_t)lua_tointeger(L, 4);
	uint32_t count = (uint32_t)lua_tointeger(L, 5);
	uint32_t reason = (uint32_t)lua_tointeger(L, 6);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->remove_counter(reason, 0, rplayer, s, o, countertype, count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_is_can_remove_counter(lua_State *L) {
	check_param_count(L, 6);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	uint32_t countertype = (uint32_t)lua_tointeger(L, 4);
	uint32_t count = (uint32_t)lua_tointeger(L, 5);
	uint32_t reason = (uint32_t)lua_tointeger(L, 6);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_remove_counter(rplayer, 0, s, o, countertype, count, reason));
	return 1;
}
int32_t scriptlib::duel_get_counter(lua_State *L) {
	check_param_count(L, 4);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	uint32_t countertype = (uint32_t)lua_tointeger(L, 4);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->get_field_counter(playerid, s, o, countertype));
	return 1;
}
int32_t scriptlib::duel_change_form(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t au = (uint32_t)lua_tointeger(L, 2);
	uint32_t ad = au, du = au, dd = au, flag = 0;
	uint32_t top = lua_gettop(L);
	if(top > 2) ad = (uint32_t)lua_tointeger(L, 3);
	if(top > 3) du = (uint32_t)lua_tointeger(L, 4);
	if(top > 4) dd = (uint32_t)lua_tointeger(L, 5);
	if(top > 5 && lua_toboolean(L, 6)) flag |= NO_FLIP_EFFECT;
	if(pcard) {
		card_set cset{ pcard };
		pduel->game_field->change_position(cset, pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, au, ad, du, dd, flag, TRUE);
	} else
		pduel->game_field->change_position(pgroup->container, pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, au, ad, du, dd, flag, TRUE);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_release(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t reason = (uint32_t)lua_tointeger(L, 2);
	uint32_t reason_player = pduel->game_field->core.reason_player;
	if (lua_gettop(L) >= 3)
		reason_player = (uint32_t)lua_tointeger(L, 3);
	if(pcard)
		pduel->game_field->release(pcard, pduel->game_field->core.reason_effect, reason, reason_player);
	else
		pduel->game_field->release(pgroup->container, pduel->game_field->core.reason_effect, reason, reason_player);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_move_to_field(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 6);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	uint32_t move_player = (uint32_t)lua_tointeger(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 3);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t destination = (uint32_t)lua_tointeger(L, 4);
	uint32_t positions = (uint32_t)lua_tointeger(L, 5);
	uint32_t enable = lua_toboolean(L, 6);
	uint32_t zone = 0xff;
	if(lua_gettop(L) > 6)
		zone = (uint32_t)lua_tointeger(L, 7);
	if(destination == LOCATION_FZONE) {
		destination = LOCATION_SZONE;
		zone = 0x1U << 5;
	}
	uint32_t pzone = FALSE;
	if(destination == LOCATION_PZONE) {
		destination = LOCATION_SZONE;
		pzone = TRUE;
	}
	duel* pduel = pcard->pduel;
	pcard->enable_field_effect(false);
	pduel->game_field->adjust_instant();
	pduel->game_field->move_to_field(pcard, move_player, playerid, destination, positions, enable, 0, pzone, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_return_to_field(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	if(!(pcard->current.reason & REASON_TEMPORARY))
		return 0;
	int32_t pos = pcard->previous.position;
	if(lua_gettop(L) >= 2)
		pos = (int32_t)lua_tointeger(L, 2);
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 3)
		zone = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = pcard->pduel;
	pcard->enable_field_effect(false);
	pduel->game_field->adjust_instant();
	pduel->game_field->refresh_location_info_instant();
	pduel->game_field->move_to_field(pcard, pcard->previous.controler, pcard->previous.controler, pcard->previous.location, pos, TRUE, RETURN_TEMP_REMOVE_TO_FIELD, pcard->previous.pzone, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_move_sequence(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	int32_t seq = (int32_t)lua_tointeger(L, 2);
	duel* pduel = pcard->pduel;
	int32_t playerid = pcard->current.controler;
	if(pcard->is_affect_by_effect(pduel->game_field->core.reason_effect)) {
		pduel->game_field->move_card(playerid, pcard, pcard->current.location, seq);
		pduel->game_field->raise_single_event(pcard, 0, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, playerid, 0);
		pduel->game_field->raise_event(pcard, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, playerid, 0);
		pduel->game_field->process_single_event();
		pduel->game_field->process_instant_event();
	}
	return 0;
}
int32_t scriptlib::duel_swap_sequence(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	check_param(L, PARAM_TYPE_CARD, 2);
	card* pcard1 = *(card**) lua_touserdata(L, 1);
	card* pcard2 = *(card**) lua_touserdata(L, 2);
	uint8_t player = pcard1->current.controler;
	uint8_t location = pcard1->current.location;
	duel* pduel = pcard1->pduel;
	if(pcard2->current.controler == player
		&& location == LOCATION_MZONE && pcard2->current.location == location
		&& pcard1->is_affect_by_effect(pduel->game_field->core.reason_effect)
		&& pcard2->is_affect_by_effect(pduel->game_field->core.reason_effect)) {
		pduel->game_field->swap_card(pcard1, pcard2);
		card_set swapped;
		swapped.insert(pcard1);
		swapped.insert(pcard2);
		pduel->game_field->raise_single_event(pcard1, 0, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, player, 0);
		pduel->game_field->raise_single_event(pcard2, 0, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, player, 0);
		pduel->game_field->raise_event(swapped, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, player, 0);
		pduel->game_field->process_single_event();
		pduel->game_field->process_instant_event();
	}
	return 0;
}
int32_t scriptlib::duel_activate_effect(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_EFFECT, 1);
	effect* peffect = *(effect**)lua_touserdata(L, 1);
	duel* pduel = peffect->pduel;
	pduel->game_field->add_process(PROCESSOR_ACTIVATE_EFFECT, 0, peffect, 0, 0, 0);
	return 0;
}
int32_t scriptlib::duel_set_chain_limit(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_FUNCTION, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t f = interpreter::get_function_handle(L, 1);
	pduel->game_field->core.chain_limit.emplace_back(f, pduel->game_field->core.reason_player);
	return 0;
}
int32_t scriptlib::duel_set_chain_limit_p(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_FUNCTION, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t f = interpreter::get_function_handle(L, 1);
	pduel->game_field->core.chain_limit_p.emplace_back(f, pduel->game_field->core.reason_player);
	return 0;
}
int32_t scriptlib::duel_get_chain_material(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	effect_set eset;
	pduel->game_field->filter_player_effect(playerid, EFFECT_CHAIN_MATERIAL, &eset);
	if(!eset.size())
		return 0;
	interpreter::effect2value(L, eset[0]);
	return 1;
}
int32_t scriptlib::duel_confirm_decktop(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	if(count >= pduel->game_field->player[playerid].list_main.size())
		count = (uint32_t)pduel->game_field->player[playerid].list_main.size();
	else if(pduel->game_field->player[playerid].list_main.size() > count) {
		if(pduel->game_field->core.global_flag & GLOBALFLAG_DECK_REVERSE_CHECK) {
			card* pcard = *(pduel->game_field->player[playerid].list_main.rbegin() + count);
			if(pduel->game_field->core.deck_reversed) {
				pduel->write_buffer8(MSG_DECK_TOP);
				pduel->write_buffer8(playerid);
				pduel->write_buffer8(count);
				if(pcard->current.position != POS_FACEUP_DEFENSE)
					pduel->write_buffer32(pcard->data.code);
				else
					pduel->write_buffer32(pcard->data.code | 0x80000000);
			}
		}
	}
	auto cit = pduel->game_field->player[playerid].list_main.rbegin();
	pduel->write_buffer8(MSG_CONFIRM_DECKTOP);
	pduel->write_buffer8(playerid);
	pduel->write_buffer8(count);
	for(uint32_t i = 0; i < count && cit != pduel->game_field->player[playerid].list_main.rend(); ++i, ++cit) {
		pduel->write_buffer32((*cit)->data.code);
		pduel->write_buffer8((*cit)->current.controler);
		pduel->write_buffer8((*cit)->current.location);
		pduel->write_buffer8((*cit)->current.sequence);
	}
	pduel->game_field->add_process(PROCESSOR_WAIT, 0, 0, 0, 0, 0);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_confirm_extratop(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	if(count >= pduel->game_field->player[playerid].list_extra.size() - pduel->game_field->player[playerid].extra_p_count)
		count = (uint32_t)pduel->game_field->player[playerid].list_extra.size() - pduel->game_field->player[playerid].extra_p_count;
	auto cit = pduel->game_field->player[playerid].list_extra.rbegin() + pduel->game_field->player[playerid].extra_p_count;
	pduel->write_buffer8(MSG_CONFIRM_EXTRATOP);
	pduel->write_buffer8(playerid);
	pduel->write_buffer8(count);
	for(uint32_t i = 0; i < count && cit != pduel->game_field->player[playerid].list_extra.rend(); ++i, ++cit) {
		pduel->write_buffer32((*cit)->data.code);
		pduel->write_buffer8((*cit)->current.controler);
		pduel->write_buffer8((*cit)->current.location);
		pduel->write_buffer8((*cit)->current.sequence);
	}
	pduel->game_field->add_process(PROCESSOR_WAIT, 0, 0, 0, 0, 0);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_confirm_cards(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 2);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 2);
		if(pgroup->container.size() == 0)
			return 0;
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 2);
	uint8_t skip_panel = 0;
	if(lua_gettop(L) >= 3)
		skip_panel = lua_toboolean(L, 3);
	pduel->write_buffer8(MSG_CONFIRM_CARDS);
	pduel->write_buffer8(playerid);
	pduel->write_buffer8(skip_panel);
	if(pcard) {
		pduel->write_buffer8(1);
		pduel->write_buffer32(pcard->data.code);
		pduel->write_buffer8(pcard->current.controler);
		pduel->write_buffer8(pcard->current.location);
		pduel->write_buffer8(pcard->current.sequence);
	} else {
		pduel->write_buffer8((uint8_t)pgroup->container.size());
		for(auto& group_card : pgroup->container) {
			pduel->write_buffer32(group_card->data.code);
			pduel->write_buffer8(group_card->current.controler);
			pduel->write_buffer8(group_card->current.location);
			pduel->write_buffer8(group_card->current.sequence);
		}
	}
	pduel->game_field->add_process(PROCESSOR_WAIT, 0, 0, 0, 0, 0);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_sort_decktop(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	uint32_t sort_player = (uint32_t)lua_tointeger(L, 1);
	uint32_t target_player = (uint32_t)lua_tointeger(L, 2);
	uint32_t count = (uint32_t)lua_tointeger(L, 3);
	if(sort_player != 0 && sort_player != 1)
		return 0;
	if(target_player != 0 && target_player != 1)
		return 0;
	if(count < 1 || count > 16)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_SORT_DECK, 0, 0, 0, sort_player + (target_player << 16), count);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_check_event(lua_State *L) {
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t ev = (int32_t)lua_tointeger(L, 1);
	int32_t get_info = lua_toboolean(L, 2);
	if(!get_info) {
		lua_pushboolean(L, pduel->game_field->check_event(ev));
		return 1;
	} else {
		tevent pe;
		if(pduel->game_field->check_event(ev, &pe)) {
			lua_pushboolean(L, 1);
			interpreter::group2value(L, pe.event_cards);
			lua_pushinteger(L, pe.event_player);
			lua_pushinteger(L, pe.event_value);
			interpreter::effect2value(L, pe.reason_effect);
			lua_pushinteger(L, pe.reason);
			lua_pushinteger(L, pe.reason_player);
			return 7;
		} else {
			lua_pushboolean(L, 0);
			return 1;
		}
	}
}
int32_t scriptlib::duel_raise_event(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 7);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t code = (uint32_t)lua_tointeger(L, 2);
	effect* peffect = nullptr;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_EFFECT, 3);
		peffect = *(effect**)lua_touserdata(L, 3);
	}
	uint32_t r = (uint32_t)lua_tointeger(L, 4);
	uint32_t rp = (uint32_t)lua_tointeger(L, 5);
	uint32_t ep = (uint32_t)lua_tointeger(L, 6);
	uint32_t ev = (uint32_t)lua_tointeger(L, 7);
	if(pcard)
		pduel->game_field->raise_event(pcard, code, peffect, r, rp, ep, ev);
	else
		pduel->game_field->raise_event(pgroup->container, code, peffect, r, rp, ep, ev);
	pduel->game_field->process_instant_event();
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_raise_single_event(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 7);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	uint32_t code = (uint32_t)lua_tointeger(L, 2);
	effect* peffect = nullptr;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_EFFECT, 3);
		peffect = *(effect**)lua_touserdata(L, 3);
	}
	uint32_t r = (uint32_t)lua_tointeger(L, 4);
	uint32_t rp = (uint32_t)lua_tointeger(L, 5);
	uint32_t ep = (uint32_t)lua_tointeger(L, 6);
	uint32_t ev = (uint32_t)lua_tointeger(L, 7);
	duel* pduel = pcard->pduel;
	pduel->game_field->raise_single_event(pcard, 0, code, peffect, r, rp, ep, ev);
	pduel->game_field->process_single_event();
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_check_timing(lua_State *L) {
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t tm = (int32_t)lua_tointeger(L, 1);
	lua_pushboolean(L, (pduel->game_field->core.hint_timing[0]&tm) || (pduel->game_field->core.hint_timing[1]&tm));
	return 1;
}
int32_t scriptlib::duel_is_environment(lua_State *L) {
	check_param_count(L, 1);
	uint32_t code = (uint32_t)lua_tointeger(L, 1);
	int32_t playerid = PLAYER_ALL;
	if(lua_gettop(L) >= 2)
		playerid = (int32_t)lua_tointeger(L, 2);
	uint32_t loc = LOCATION_ONFIELD;
	if(lua_gettop(L) >= 3)
		loc = (uint32_t)lua_tointeger(L, 3);
	if(playerid != 0 && playerid != 1 && playerid != PLAYER_ALL)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	int32_t ret = 0, fc = 0;
	if(loc & (LOCATION_FZONE + LOCATION_SZONE)) {
		card* pcard = pduel->game_field->player[0].list_szone[5];
		if(pcard && pcard->is_position(POS_FACEUP)) {
			fc = 1;
			if(code == pcard->get_code() && (playerid == 0 || playerid == PLAYER_ALL))
				ret = 1;
		}
		pcard = pduel->game_field->player[1].list_szone[5];
		if(pcard && pcard->is_position(POS_FACEUP)) {
			fc = 1;
			if(code == pcard->get_code() && (playerid == 1 || playerid == PLAYER_ALL))
				ret = 1;
		}
	}
	if(!ret && (loc & LOCATION_SZONE)) {
		if(playerid == 0 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[0].list_szone) {
				if(pcard && pcard->is_position(POS_FACEUP) && code == pcard->get_code())
					ret = 1;
			}
		}
		if(playerid == 1 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[1].list_szone) {
				if(pcard && pcard->is_position(POS_FACEUP) && code == pcard->get_code())
					ret = 1;
			}
		}
	}
	if(!ret && (loc & LOCATION_MZONE)) {
		if(playerid == 0 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[0].list_mzone) {
				if(pcard && pcard->is_position(POS_FACEUP) && code == pcard->get_code())
					ret = 1;
			}
		}
		if(playerid == 1 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[1].list_mzone) {
				if(pcard && pcard->is_position(POS_FACEUP) && code == pcard->get_code())
					ret = 1;
			}
		}
	}
	if(!ret && (loc & LOCATION_GRAVE)) {
		if(playerid == 0 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[0].list_grave) {
				if(code == pcard->get_code())
					ret = 1;
			}
		}
		if(playerid == 1 || playerid == PLAYER_ALL) {
			for(auto& pcard : pduel->game_field->player[1].list_grave) {
				if(code == pcard->get_code())
					ret = 1;
			}
		}
	}
	if(!fc) {
		effect_set eset;
		pduel->game_field->filter_field_effect(EFFECT_CHANGE_ENVIRONMENT, &eset);
		if(eset.size()) {
			effect* peffect = eset.back();
			if(code == (uint32_t)peffect->get_value() && (playerid == peffect->get_handler_player() || playerid == PLAYER_ALL))
				ret = 1;
		}
	}
	lua_pushboolean(L, ret);
	return 1;
}
int32_t scriptlib::duel_win(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t reason = (uint32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1 && playerid != 2)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->core.win_player == 5) {
		pduel->game_field->core.win_player = playerid;
		pduel->game_field->core.win_reason = reason;
	}
	return 0;
}
int32_t scriptlib::duel_draw(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t count = (int32_t)lua_tointeger(L, 2);
	if (count < 0)
		count = 0;
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->draw(pduel->game_field->core.reason_effect, reason, pduel->game_field->core.reason_player, playerid, count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_damage(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t amount = (int32_t)lua_tonumber(L, 2);
	if(amount < 0)
		amount = 0;
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	uint32_t is_step = FALSE;
	if(lua_gettop(L) >= 4)
		is_step = lua_toboolean(L, 4);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->damage(pduel->game_field->core.reason_effect, reason, pduel->game_field->core.reason_player, 0, playerid, amount, is_step);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_recover(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t amount = (int32_t)lua_tonumber(L, 2);
	if(amount < 0)
		amount = 0;
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	uint32_t is_step = FALSE;
	if(lua_gettop(L) >= 4)
		is_step = lua_toboolean(L, 4);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->recover(pduel->game_field->core.reason_effect, reason, pduel->game_field->core.reason_player, playerid, amount, is_step);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_rd_complete(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.subunits.splice(pduel->game_field->core.subunits.end(), pduel->game_field->core.recover_damage_reserve);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_equip(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_CARD, 2);
	check_param(L, PARAM_TYPE_CARD, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* equip_card = *(card**) lua_touserdata(L, 2);
	card* target = *(card**) lua_touserdata(L, 3);
	uint32_t up = TRUE;
	if(lua_gettop(L) > 3)
		up = lua_toboolean(L, 4);
	uint32_t step = FALSE;
	if(lua_gettop(L) > 4)
		step = lua_toboolean(L, 5);
	duel* pduel = target->pduel;
	pduel->game_field->equip(playerid, equip_card, target, up, step);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_equip_complete(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	card_set etargets;
	for(auto& equip_card : pduel->game_field->core.equiping_cards) {
		if(equip_card->is_position(POS_FACEUP))
			equip_card->enable_field_effect(true);
		etargets.insert(equip_card->equiping_target);
	}
	pduel->game_field->adjust_instant();
	for(auto& equip_target : etargets)
		pduel->game_field->raise_single_event(equip_target, &pduel->game_field->core.equiping_cards, EVENT_EQUIP,
		                                      pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, PLAYER_NONE, 0);
	pduel->game_field->raise_event(pduel->game_field->core.equiping_cards, EVENT_EQUIP,
	                               pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, PLAYER_NONE, 0);
	pduel->game_field->core.hint_timing[0] |= TIMING_EQUIP;
	pduel->game_field->core.hint_timing[1] |= TIMING_EQUIP;
	pduel->game_field->process_single_event();
	pduel->game_field->process_instant_event();
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_get_control(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t reset_phase = 0;
	uint32_t reset_count = 0;
	if(lua_gettop(L) >= 3) {
		reset_phase = lua_tointeger(L, 3) & 0x3ff;
		reset_count = lua_tointeger(L, 4) & 0xff;
	}
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 5)
		zone = (uint32_t)lua_tointeger(L, 5);
	if(pcard)
		pduel->game_field->get_control(pcard, pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, playerid, reset_phase, reset_count, zone);
	else
		pduel->game_field->get_control(pgroup->container, pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, playerid, reset_phase, reset_count, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_swap_control(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	card* pcard1 = nullptr;
	card* pcard2 = nullptr;
	group* pgroup1 = nullptr;
	group* pgroup2 = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE) && check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
		pcard1 = *(card**) lua_touserdata(L, 1);
		pcard2 = *(card**) lua_touserdata(L, 2);
		pduel = pcard1->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE) && check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
		pgroup1 = *(group**) lua_touserdata(L, 1);
		pgroup2 = *(group**) lua_touserdata(L, 2);
		pduel = pgroup1->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t reset_phase = 0;
	uint32_t reset_count = 0;
	if(lua_gettop(L) > 2) {
		reset_phase = lua_tointeger(L, 3) & 0x3ff;
		reset_count = lua_tointeger(L, 4) & 0xff;
	}
	if(pcard1)
		pduel->game_field->swap_control(pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, pcard1, pcard2, reset_phase, reset_count);
	else
		pduel->game_field->swap_control(pduel->game_field->core.reason_effect, pduel->game_field->core.reason_player, pgroup1->container, pgroup2->container, reset_phase, reset_count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_check_lp_cost(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t cost = (int32_t)lua_tointeger(L, 2);
	if(cost <= 0) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t must_pay = FALSE;
	if(lua_gettop(L) > 2)
		must_pay = lua_toboolean(L, 3);
	lua_pushboolean(L, pduel->game_field->check_lp_cost(playerid, cost, must_pay));
	return 1;
}
int32_t scriptlib::duel_pay_lp_cost(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t cost = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t must_pay = FALSE;
	if(lua_gettop(L) > 2)
		must_pay = lua_toboolean(L, 3);
	pduel->game_field->add_process(PROCESSOR_PAY_LPCOST, 0, 0, 0, playerid, cost, must_pay);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_discard_deck(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	uint32_t reason = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_DISCARD_DECK, 0, 0, 0, playerid + (count << 16), reason);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_discard_hand(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 5);
	if(!lua_isnil(L, 2))
		check_param(L, PARAM_TYPE_FUNCTION, 2);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	uint32_t extraargs = 0;
	if(lua_gettop(L) >= 6) {
		if(check_param(L, PARAM_TYPE_CARD, 6, TRUE))
			pexception = *(card**) lua_touserdata(L, 6);
		else if(check_param(L, PARAM_TYPE_GROUP, 6, TRUE))
			pexgroup = *(group**) lua_touserdata(L, 6);
		extraargs = lua_gettop(L) - 6;
	}
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t min = (uint32_t)lua_tointeger(L, 3);
	uint32_t max = (uint32_t)lua_tointeger(L, 4);
	uint32_t reason = (uint32_t)lua_tointeger(L, 5);
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_matching_card(L, 2, playerid, LOCATION_HAND, 0, pgroup, pexception, pexgroup, extraargs);
	pduel->game_field->core.select_cards.assign(pgroup->container.begin(), pgroup->container.end());
	if(pduel->game_field->core.select_cards.size() == 0) {
		lua_pushinteger(L, 0);
		return 1;
	}
	pduel->game_field->add_process(PROCESSOR_DISCARD_HAND, 0, nullptr, nullptr, playerid, min + (max << 16), reason);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_disable_shuffle_check(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint8_t disable = TRUE;
	if(lua_gettop(L) > 0)
		disable = lua_toboolean(L, 1);
	pduel->game_field->core.shuffle_check_disabled = disable;
	return 0;
}
int32_t scriptlib::duel_disable_self_destroy_check(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint8_t disable = TRUE;
	if(lua_gettop(L) > 0)
		disable = lua_toboolean(L, 1);
	pduel->game_field->core.selfdes_disabled = disable;
	return 0;
}
int32_t scriptlib::duel_reveal_select_deck_sequence(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint8_t reveal = TRUE;
	if(lua_gettop(L) > 0)
		reveal = lua_toboolean(L, 1);
	pduel->game_field->core.select_deck_sequence_revealed = reveal;
	return 0;
}
int32_t scriptlib::duel_shuffle_deck(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->shuffle(playerid, LOCATION_DECK);
	return 0;
}
int32_t scriptlib::duel_shuffle_extra(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if (playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->shuffle(playerid, LOCATION_EXTRA);
	return 0;
}
int32_t scriptlib::duel_shuffle_hand(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->shuffle(playerid, LOCATION_HAND);
	return 0;
}
int32_t scriptlib::duel_shuffle_setcard(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_GROUP, 1);
	group* pgroup = *(group**)lua_touserdata(L, 1);
	if(pgroup->container.size() <= 1)
		return 0;
	duel* pduel = pgroup->pduel;
	card* ms[5]{};
	uint8_t seq[5]{};
	uint8_t tp = 2;
	uint8_t loc = 0;
	uint8_t ct = 0;
	for(auto& pcard : pgroup->container) {
		if((loc != 0 && (pcard->current.location != loc)) || (pcard->current.location != LOCATION_MZONE && pcard->current.location != LOCATION_SZONE)
			|| (pcard->current.position & POS_FACEUP) || (pcard->current.sequence > 4) || (tp != 2 && (pcard->current.controler != tp)))
			return 0;
		tp = pcard->current.controler;
		loc = pcard->current.location;
		ms[ct] = pcard;
		seq[ct] = pcard->current.sequence;
		++ct;
	}
	for(int32_t i = ct - 1; i > 0; --i) {
		int32_t s = pduel->get_next_integer(0, i);
		std::swap(ms[i], ms[s]);
	}
	pduel->write_buffer8(MSG_SHUFFLE_SET_CARD);
	pduel->write_buffer8(loc);
	pduel->write_buffer8(ct);
	for(int32_t i = 0; i < ct; ++i) {
		card* pcard = ms[i];
		pduel->write_buffer32(pcard->get_info_location());
		if(loc == LOCATION_MZONE)
			pduel->game_field->player[tp].list_mzone[seq[i]] = pcard;
		else
			pduel->game_field->player[tp].list_szone[seq[i]] = pcard;
		pcard->current.sequence = seq[i];
		pduel->game_field->raise_single_event(pcard, 0, EVENT_MOVE, pcard->current.reason_effect, pcard->current.reason, pcard->current.reason_player, tp, 0);
	}
	pduel->game_field->raise_event(pgroup->container, EVENT_MOVE, pduel->game_field->core.reason_effect, 0, pduel->game_field->core.reason_player, tp, 0);
	pduel->game_field->process_single_event();
	pduel->game_field->process_instant_event();
	for(int32_t i = 0; i < ct; ++i) {
		if(ms[i]->xyz_materials.size())
			pduel->write_buffer32(ms[i]->get_info_location());
		else
			pduel->write_buffer32(0);
	}
	return 0;
}
int32_t scriptlib::duel_change_attacker(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* attacker = *(card**) lua_touserdata(L, 1);
	int32_t ignore_count = FALSE;
	if(lua_gettop(L) >= 2)
		ignore_count = lua_toboolean(L, 2);
	duel* pduel = attacker->pduel;
	if(pduel->game_field->core.attacker == attacker)
		return 0;
	card* attack_target = pduel->game_field->core.attack_target;
	++pduel->game_field->core.attacker->announce_count;
	pduel->game_field->core.attacker->announced_cards.addcard(attack_target);
	pduel->game_field->attack_all_target_check();
	pduel->game_field->core.attacker = attacker;
	attacker->attack_controler = attacker->current.controler;
	pduel->game_field->core.pre_field[0] = attacker->fieldid_r;
	if(!ignore_count) {
		++attacker->attack_announce_count;
		if(pduel->game_field->infos.phase == PHASE_DAMAGE) {
			++attacker->attacked_count;
			attacker->attacked_cards.addcard(attack_target);
		}
	}
	return 0;
}
int32_t scriptlib::duel_change_attack_target(lua_State *L) {
	check_param_count(L, 1);
	duel* pduel;
	card* target;
	if(lua_isnil(L, 1)) {
		pduel = interpreter::get_duel_info(L);
		target = 0;
	} else {
		check_param(L, PARAM_TYPE_CARD, 1);
		target = *(card**)lua_touserdata(L, 1);
		pduel = target->pduel;
	}
	card* attacker = pduel->game_field->core.attacker;
	if(!attacker || !attacker->is_capable_attack() || attacker->is_status(STATUS_ATTACK_CANCELED)) {
		lua_pushboolean(L, 0);
		return 1;
	}
	card_vector cv;
	pduel->game_field->get_attack_target(attacker, &cv, pduel->game_field->core.chain_attack);
	if(target && std::find(cv.begin(), cv.end(), target) != cv.end()
		|| !target && !attacker->is_affected_by_effect(EFFECT_CANNOT_DIRECT_ATTACK)) {
		pduel->game_field->core.attack_target = target;
		pduel->game_field->core.attack_rollback = FALSE;
		pduel->game_field->core.opp_mzone.clear();
		uint8_t turnp = pduel->game_field->infos.turn_player;
		for(auto& pcard : pduel->game_field->player[1 - turnp].list_mzone) {
			if(pcard)
				pduel->game_field->core.opp_mzone.insert(pcard->fieldid_r);
		}
		pduel->write_buffer8(MSG_ATTACK);
		pduel->write_buffer32(attacker->get_info_location());
		if(target) {
			pduel->game_field->raise_single_event(target, 0, EVENT_BE_BATTLE_TARGET, 0, REASON_REPLACE, 0, 1 - turnp, 0);
			pduel->game_field->raise_event(target, EVENT_BE_BATTLE_TARGET, 0, REASON_REPLACE, 0, 1 - turnp, 0);
			pduel->game_field->process_single_event();
			pduel->game_field->process_instant_event();
			pduel->write_buffer32(target->get_info_location());
		} else {
			pduel->game_field->core.attack_player = TRUE;
			pduel->write_buffer32(0);
		}
		lua_pushboolean(L, 1);
	} else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_calculate_damage(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* attacker = *(card**)lua_touserdata(L, 1);
	card* attack_target;
	if(lua_isnil(L, 2))
		attack_target = 0;
	else {
		check_param(L, PARAM_TYPE_CARD, 2);
		attack_target = *(card**)lua_touserdata(L, 2);
	}
	int32_t new_attack = FALSE;
	if(lua_gettop(L) >= 3)
		new_attack = lua_toboolean(L, 3);
	if(attacker == attack_target)
		return 0;
	attacker->pduel->game_field->add_process(PROCESSOR_DAMAGE_STEP, 0, (effect*)attacker, (group*)attack_target, 0, new_attack);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_get_battle_damage(lua_State *L) {
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	lua_pushinteger(L, pduel->game_field->core.battle_damage[playerid]);
	return 1;
}
int32_t scriptlib::duel_change_battle_damage(lua_State *L) {
	check_param_count(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t dam = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t check = TRUE;
	if(lua_gettop(L) >= 3)
		check = lua_toboolean(L, 3);
	if(check && pduel->game_field->core.battle_damage[playerid] == 0)
		return 0;
	pduel->game_field->core.battle_damage[playerid] = dam;
	return 0;
}
int32_t scriptlib::duel_change_target(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_GROUP, 2);
	uint32_t count = (uint32_t)lua_tointeger(L, 1);
	group* pgroup = *(group**)lua_touserdata(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->change_target(count, pgroup);
	return 0;
}
int32_t scriptlib::duel_change_target_player(lua_State *L) {
	check_param_count(L, 2);
	uint32_t count = (uint32_t)lua_tointeger(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->change_target_player(count, playerid);
	return 0;
}
int32_t scriptlib::duel_change_target_param(lua_State *L) {
	check_param_count(L, 2);
	uint32_t count = (uint32_t)lua_tointeger(L, 1);
	uint32_t param = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->change_target_param(count, param);
	return 0;
}
int32_t scriptlib::duel_break_effect(lua_State *L) {
	check_action_permission(L);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->break_effect();
	pduel->game_field->raise_event(nullptr, EVENT_BREAK_EFFECT, 0, 0, PLAYER_NONE, PLAYER_NONE, 0);
	pduel->game_field->process_instant_event();
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_change_effect(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_FUNCTION, 2);
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t count = (uint32_t)lua_tointeger(L, 1);
	int32_t pf = interpreter::get_function_handle(L, 2);
	pduel->game_field->change_chain_effect(count, pf);
	return 0;
}
int32_t scriptlib::duel_negate_activate(lua_State *L) {
	check_param_count(L, 1);
	uint32_t c = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->negate_chain(c));
	return 1;
}
int32_t scriptlib::duel_negate_effect(lua_State *L) {
	check_param_count(L, 1);
	uint32_t c = (uint32_t)lua_tointeger(L, 1);
	uint8_t forced = FALSE;
	if(lua_gettop(L) > 1)
		forced = lua_toboolean(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->disable_chain(c, forced));
	return 1;
}
int32_t scriptlib::duel_negate_related_chain(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**)lua_touserdata(L, 1);
	uint32_t reset_flag = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = pcard->pduel;
	if(pduel->game_field->core.current_chain.size() < 2)
		return 0;
	if(!pcard->is_affect_by_effect(pduel->game_field->core.reason_effect))
		return 0;
	for(auto it = pduel->game_field->core.current_chain.rbegin(); it != pduel->game_field->core.current_chain.rend(); ++it) {
		if(it->triggering_effect->get_handler() == pcard && pcard->is_has_relation(*it)) {
			effect* negeff = pduel->new_effect();
			negeff->owner = pduel->game_field->core.reason_effect->get_handler();
			negeff->type = EFFECT_TYPE_SINGLE;
			negeff->code = EFFECT_DISABLE_CHAIN;
			negeff->value = it->chain_id;
			negeff->reset_flag = RESET_CHAIN | RESET_EVENT | reset_flag;
			pcard->add_effect(negeff);
		}
	}
	return 0;
}
int32_t scriptlib::duel_disable_summon(lua_State *L) {
	check_param_count(L, 1);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**)lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**)lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	uint32_t sumtype = 0;
	if (pduel->game_field->check_event(EVENT_SUMMON)) {
		if (pduel->game_field->core.is_gemini_summoning)
			sumtype = SUMMON_TYPE_DUAL;
		else
			sumtype = SUMMON_TYPE_NORMAL;
	}
	else if (pduel->game_field->check_event(EVENT_FLIP_SUMMON))
		sumtype = SUMMON_TYPE_FLIP;
	else if (pduel->game_field->check_event(EVENT_SPSUMMON))
		sumtype = SUMMON_TYPE_SPECIAL;
	else
		return 0;
	if (sumtype & SUMMON_TYPE_NORMAL || sumtype & SUMMON_TYPE_FLIP) {
		if (pgroup && pgroup->container.size() != 1)
			return 0;
		if (!pcard)
			pcard = *pgroup->container.begin();
	}
	uint8_t sumplayer = PLAYER_NONE;
	effect* reason_effect = pduel->game_field->core.reason_effect;
	card_set negated_cards;
	if (sumtype == SUMMON_TYPE_DUAL || sumtype & SUMMON_TYPE_FLIP) {
		if (!pcard->is_summon_negatable(sumtype, reason_effect))
			return 0;
		sumplayer = pcard->summon_player;
		pcard->set_status(STATUS_FLIP_SUMMONING, FALSE);
		pcard->set_status(STATUS_FLIP_SUMMON_DISABLED, TRUE);
	}
	else {
		if (pcard) {
			if (!pcard->is_summon_negatable(sumtype, reason_effect))
				return 0;
			sumplayer = pcard->summon_player;
			pcard->set_status(STATUS_SUMMONING, FALSE);
			pcard->set_status(STATUS_SUMMON_DISABLED, TRUE);
			pcard->set_status(STATUS_PROC_COMPLETE, FALSE);
		}
		else {
			for (auto& group_card : pgroup->container) {
				if (!group_card->is_summon_negatable(sumtype, reason_effect))
					continue;
				sumplayer = group_card->summon_player;
				group_card->set_status(STATUS_SUMMONING, FALSE);
				group_card->set_status(STATUS_SUMMON_DISABLED, TRUE);
				group_card->set_status(STATUS_PROC_COMPLETE, FALSE);
				negated_cards.insert(group_card);
			}
			if (!negated_cards.size())
				return 0;
		}
	}
	pduel->game_field->core.is_summon_negated = true;
	uint32_t event_code = 0;
	if(sumtype & SUMMON_TYPE_NORMAL)
		event_code = EVENT_SUMMON_NEGATED;
	else if(sumtype & SUMMON_TYPE_FLIP)
		event_code = EVENT_FLIP_SUMMON_NEGATED;
	else if(sumtype & SUMMON_TYPE_SPECIAL)
		event_code = EVENT_SPSUMMON_NEGATED;
	uint8_t reason_player = pduel->game_field->core.reason_player;
	if(pcard)
		pduel->game_field->raise_event(pcard, event_code, reason_effect, REASON_EFFECT, reason_player, sumplayer, 0);
	else
		pduel->game_field->raise_event(pgroup->container, event_code, reason_effect, REASON_EFFECT, reason_player, sumplayer, 0);
	pduel->game_field->process_instant_event();
	return 0;
}
int32_t scriptlib::duel_increase_summon_count(lua_State *L) {
	card* pcard = nullptr;
	effect* pextra = nullptr;
	if(lua_gettop(L) > 0) {
		check_param(L, PARAM_TYPE_CARD, 1);
		pcard = *(card**) lua_touserdata(L, 1);
	}
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = pduel->game_field->core.reason_player;
	if(pcard && (pextra = pcard->is_affected_by_effect(EFFECT_EXTRA_SUMMON_COUNT)))
		pextra->get_value(pcard);
	else
		++pduel->game_field->core.summon_count[playerid];
	return 0;
}
int32_t scriptlib::duel_check_summon_count(lua_State *L) {
	card* pcard = nullptr;
	if(lua_gettop(L) > 0) {
		check_param(L, PARAM_TYPE_CARD, 1);
		pcard = *(card**) lua_touserdata(L, 1);
	}
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = pduel->game_field->core.reason_player;
	if((pcard && pcard->is_affected_by_effect(EFFECT_EXTRA_SUMMON_COUNT))
	        || pduel->game_field->core.summon_count[playerid] < pduel->game_field->get_summon_count_limit(playerid))
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
// Return usable count in zone of playerid's Main MZONE or SZONE(0~4) when uplayer moves a card to playerid's field (can be negative).
int32_t scriptlib::duel_get_location_count(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t location = (uint32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t uplayer = pduel->game_field->core.reason_player;
	uint32_t reason = LOCATION_REASON_TOFIELD;
	if(lua_gettop(L) >= 3 && !lua_isnil(L,3))
		uplayer = (uint32_t)lua_tointeger(L, 3);
	if(lua_gettop(L) >= 4)
		reason = (uint32_t)lua_tointeger(L, 4);
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 5)
		zone = (uint32_t)lua_tointeger(L, 5);
	uint32_t list = 0;
	lua_pushinteger(L, pduel->game_field->get_useable_count(nullptr, playerid, location, uplayer, reason, zone, &list));
	lua_pushinteger(L, list);
	return 2;
}
// Return usable count in zone of playerid's Main MZONE after mcard or mgroup leaves the field.
int32_t scriptlib::duel_get_mzone_count(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	bool swapped = false;
	card* mcard = nullptr;
	group* mgroup = nullptr;
	uint32_t used_location[2] = { 0, 0 };
	card_vector list_mzone[2];
	if(lua_gettop(L) >= 2 && !lua_isnil(L, 2)) {
		if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
			mcard = *(card**)lua_touserdata(L, 2);
		} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
			mgroup = *(group**)lua_touserdata(L, 2);
		} else
			return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 2);
		for(int32_t p = 0; p < 2; p++) {
			uint32_t digit = 0x1U;
			for(auto& pcard : pduel->game_field->player[p].list_mzone) {
				if(pcard && pcard != mcard && !(mgroup && mgroup->container.find(pcard) != mgroup->container.end())) {
					used_location[p] |= digit;
					list_mzone[p].push_back(pcard);
				} else
					list_mzone[p].push_back(nullptr);
				digit <<= 1;
			}
			used_location[p] |= pduel->game_field->player[p].used_location & 0xff00;
			std::swap(used_location[p], pduel->game_field->player[p].used_location);
			pduel->game_field->player[p].list_mzone.swap(list_mzone[p]);
		}
		swapped = true;
	}
	uint32_t uplayer = pduel->game_field->core.reason_player;
	uint32_t reason = LOCATION_REASON_TOFIELD;
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 3)
		uplayer = (uint32_t)lua_tointeger(L, 3);
	if(lua_gettop(L) >= 4)
		reason = (uint32_t)lua_tointeger(L, 4);
	if(lua_gettop(L) >= 5)
		zone = (uint32_t)lua_tointeger(L, 5);
	uint32_t list = 0;
	lua_pushinteger(L, pduel->game_field->get_useable_count(nullptr, playerid, LOCATION_MZONE, uplayer, reason, zone, &list));
	lua_pushinteger(L, list);
	if(swapped) {
		pduel->game_field->player[0].used_location = used_location[0];
		pduel->game_field->player[1].used_location = used_location[1];
		pduel->game_field->player[0].list_mzone.swap(list_mzone[0]);
		pduel->game_field->player[1].list_mzone.swap(list_mzone[1]);
	}
	return 2;
}
// Return usable count in zone of playerid's SZONE after card or group leaves the field.
int32_t scriptlib::duel_get_szone_count(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	bool swapped = false;
	card* mcard = nullptr;
	group* mgroup = nullptr;
	uint32_t used_location[2] = { 0, 0 };
	card_vector list_szone[2];
	if(lua_gettop(L) >= 2 && !lua_isnil(L, 2)) {
		if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
			mcard = *(card**)lua_touserdata(L, 2);
		} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
			mgroup = *(group**)lua_touserdata(L, 2);
		} else
			return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 2);
		for(int32_t p = 0; p < 2; p++) {
			uint32_t digit = 0x100U;
			for(auto& pcard : pduel->game_field->player[p].list_szone) {
				if(pcard && pcard != mcard && !(mgroup && mgroup->container.find(pcard) != mgroup->container.end())) {
					used_location[p] |= digit;
					list_szone[p].push_back(pcard);
				} else
					list_szone[p].push_back(nullptr);
				digit <<= 1;
			}
			used_location[p] |= pduel->game_field->player[p].used_location & 0xff;
			std::swap(used_location[p], pduel->game_field->player[p].used_location);
			pduel->game_field->player[p].list_szone.swap(list_szone[p]);
		}
		swapped = true;
	}
	uint32_t uplayer = pduel->game_field->core.reason_player;
	uint32_t reason = LOCATION_REASON_TOFIELD;
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 3)
		uplayer = (uint32_t)lua_tointeger(L, 3);
	if(lua_gettop(L) >= 4)
		reason = (uint32_t)lua_tointeger(L, 4);
	if(lua_gettop(L) >= 5)
		zone = (uint32_t)lua_tointeger(L, 5);
	uint32_t list = 0;
	lua_pushinteger(L, pduel->game_field->get_useable_count(nullptr, playerid, LOCATION_SZONE, uplayer, reason, zone, &list));
	lua_pushinteger(L, list);
	if(swapped) {
		pduel->game_field->player[0].used_location = used_location[0];
		pduel->game_field->player[1].used_location = used_location[1];
		pduel->game_field->player[0].list_szone.swap(list_szone[0]);
		pduel->game_field->player[1].list_szone.swap(list_szone[1]);
	}
	return 2;
}
// Condition: uplayer moves scard or any card with type from Extra Deck to playerid's field
// Return usable count in zone of playerid's MZONE after mcard or mgroup leaves the field
int32_t scriptlib::duel_get_location_count_fromex(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t uplayer = pduel->game_field->core.reason_player;
	if(lua_gettop(L) >= 2 && !lua_isnil(L, 2))
		uplayer = (uint32_t)lua_tointeger(L, 2);
	bool swapped = false;
	card* mcard = nullptr;
	group* mgroup = nullptr;
	uint32_t used_location[2] = {0, 0};
	card_vector list_mzone[2];
	if(lua_gettop(L) >= 3 && !lua_isnil(L, 3)) {
		if(check_param(L, PARAM_TYPE_CARD, 3, TRUE)) {
			mcard = *(card**) lua_touserdata(L, 3);
		} else if(check_param(L, PARAM_TYPE_GROUP, 3, TRUE)) {
			mgroup = *(group**) lua_touserdata(L, 3);
		} else
			return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 3);
		for(int32_t p = 0; p < 2; p++) {
			uint32_t digit = 1;
			for(auto& pcard : pduel->game_field->player[p].list_mzone) {
				if(pcard && pcard != mcard && !(mgroup && mgroup->container.find(pcard) != mgroup->container.end())) {
					used_location[p] |= digit;
					list_mzone[p].push_back(pcard);
				} else
					list_mzone[p].push_back(nullptr);
				digit <<= 1;
			}
			used_location[p] |= pduel->game_field->player[p].used_location & 0xff00;
			std::swap(used_location[p], pduel->game_field->player[p].used_location);
			pduel->game_field->player[p].list_mzone.swap(list_mzone[p]);
		}
		swapped = true;
	}
	bool use_temp_card = false;
	card* scard = nullptr;
	if(lua_gettop(L) >= 4 && !lua_isnil(L, 4)) {
		if(check_param(L, PARAM_TYPE_CARD, 4, TRUE)) {
			scard = *(card**)lua_touserdata(L, 4);
		} else {
			use_temp_card = true;
			uint32_t type = (uint32_t)lua_tointeger(L, 4);
			scard = pduel->game_field->temp_card;
			scard->current.location = LOCATION_EXTRA;
			scard->data.type = TYPE_MONSTER | type;
			if(type & TYPE_PENDULUM)
				scard->current.position = POS_FACEUP_DEFENSE;
			else
				scard->current.position = POS_FACEDOWN_DEFENSE;
		}
	}
	uint32_t zone = 0xff;
	if(lua_gettop(L) >= 5)
		zone = (uint32_t)lua_tointeger(L, 5);
	uint32_t list = 0;
	lua_pushinteger(L, pduel->game_field->get_useable_count_fromex(scard, playerid, uplayer, zone, &list));
	lua_pushinteger(L, list);
	if(swapped) {
		pduel->game_field->player[0].used_location = used_location[0];
		pduel->game_field->player[1].used_location = used_location[1];
		pduel->game_field->player[0].list_mzone.swap(list_mzone[0]);
		pduel->game_field->player[1].list_mzone.swap(list_mzone[1]);
	}
	if(use_temp_card) {
		scard->current.location = 0;
		scard->data.type = 0;
		scard->current.position = 0;
	}
	return 2;
}
// Return the number of available slots in the Main MZONE and Extra MZONE of `playerid`.
int32_t scriptlib::duel_get_usable_mzone_count(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t uplayer = pduel->game_field->core.reason_player;
	if(lua_gettop(L) >= 2)
		uplayer = (uint32_t)lua_tointeger(L, 2);
	uint32_t zone = 0xff;
	uint32_t flag1, flag2;
	int32_t ct1 = pduel->game_field->get_tofield_count(nullptr, playerid, LOCATION_MZONE, uplayer, LOCATION_REASON_TOFIELD, zone, &flag1);
	int32_t ct2 = pduel->game_field->get_spsummonable_count_fromex(nullptr, playerid, uplayer, zone, &flag2);
	int32_t ct3 = field::field_used_count[~(flag1 | flag2) & 0x1f];
	int32_t count = ct1 + ct2 - ct3;
	int32_t limit = pduel->game_field->get_mzone_limit(playerid, uplayer, LOCATION_REASON_TOFIELD);
	if(count > limit)
		count = limit;
	lua_pushinteger(L, count);
	return 1;
}
int32_t scriptlib::duel_get_linked_group(lua_State *L) {
	check_param_count(L, 3);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	card_set cset;
	pduel->game_field->get_linked_cards(rplayer, s, o, &cset);
	group* pgroup = pduel->new_group(cset);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_get_linked_group_count(lua_State *L) {
	check_param_count(L, 3);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	card_set cset;
	pduel->game_field->get_linked_cards(rplayer, s, o, &cset);
	lua_pushinteger(L, cset.size());
	return 1;
}
int32_t scriptlib::duel_get_linked_zone(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->get_linked_zone(playerid));
	return 1;
}
int32_t scriptlib::duel_get_field_card(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t location = (uint32_t)lua_tointeger(L, 2);
	uint32_t sequence = (uint32_t)lua_tointeger(L, 3);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	card* pcard = pduel->game_field->get_field_card(playerid, location, sequence);
	if(!pcard || pcard->get_status(STATUS_SUMMONING | STATUS_SPSUMMON_STEP))
		return 0;
	interpreter::card2value(L, pcard);
	return 1;
}
int32_t scriptlib::duel_check_location(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t location = (uint32_t)lua_tointeger(L, 2);
	uint32_t sequence = (uint32_t)lua_tointeger(L, 3);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_location_useable(playerid, location, sequence));
	return 1;
}
int32_t scriptlib::duel_get_current_chain(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->core.current_chain.size());
	return 1;
}
int32_t scriptlib::duel_get_ready_chain(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	int32_t size = (int32_t)pduel->game_field->core.current_chain.size();
	if (size && !pduel->game_field->core.is_target_ready) {
		--size;
	}
	lua_pushinteger(L, size);
	return 1;
}
int32_t scriptlib::duel_get_chain_info(lua_State *L) {
	check_param_count(L, 1);
	uint32_t c = (uint32_t)lua_tointeger(L, 1);
	uint32_t args = lua_gettop(L) - 1;
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(c);
	if(!ch)
		return 0;
	for(uint32_t i = 0; i < args; ++i) {
		uint32_t flag = (uint32_t)lua_tointeger(L, 2 + i);
		switch(flag) {
		case CHAININFO_CHAIN_COUNT:
			lua_pushinteger(L, ch->chain_count);
			break;
		case CHAININFO_TRIGGERING_EFFECT:
			interpreter::effect2value(L, ch->triggering_effect);
			break;
		case CHAININFO_TRIGGERING_PLAYER:
			lua_pushinteger(L, ch->triggering_player);
			break;
		case CHAININFO_TRIGGERING_CONTROLER:
			lua_pushinteger(L, ch->triggering_controler);
			break;
		case CHAININFO_TRIGGERING_LOCATION:
			lua_pushinteger(L, ch->triggering_location);
			break;
		case CHAININFO_TRIGGERING_SEQUENCE:
			lua_pushinteger(L, ch->triggering_sequence);
			break;
		case CHAININFO_TRIGGERING_POSITION:
			lua_pushinteger(L, ch->triggering_position);
			break;
		case CHAININFO_TRIGGERING_CODE:
			lua_pushinteger(L, ch->triggering_state.code);
			break;
		case CHAININFO_TRIGGERING_CODE2:
			lua_pushinteger(L, ch->triggering_state.code2);
			break;
		case CHAININFO_TRIGGERING_LEVEL:
			lua_pushinteger(L, ch->triggering_state.level);
			break;
		case CHAININFO_TRIGGERING_RANK:
			lua_pushinteger(L, ch->triggering_state.rank);
			break;
		case CHAININFO_TRIGGERING_ATTRIBUTE:
			lua_pushinteger(L, ch->triggering_state.attribute);
			break;
		case CHAININFO_TRIGGERING_RACE:
			lua_pushinteger(L, ch->triggering_state.race);
			break;
		case CHAININFO_TRIGGERING_ATTACK:
			lua_pushinteger(L, ch->triggering_state.attack);
			break;
		case CHAININFO_TRIGGERING_DEFENSE:
			lua_pushinteger(L, ch->triggering_state.defense);
			break;
		case CHAININFO_TARGET_CARDS:
			interpreter::group2value(L, ch->target_cards);
			break;
		case CHAININFO_TARGET_PLAYER:
			lua_pushinteger(L, ch->target_player);
			break;
		case CHAININFO_TARGET_PARAM:
			lua_pushinteger(L, ch->target_param);
			break;
		case CHAININFO_DISABLE_REASON:
			interpreter::effect2value(L, ch->disable_reason);
			break;
		case CHAININFO_DISABLE_PLAYER:
			lua_pushinteger(L, ch->disable_player);
			break;
		case CHAININFO_CHAIN_ID:
			lua_pushinteger(L, ch->chain_id);
			break;
		case CHAININFO_TYPE:
			if((ch->triggering_effect->card_type & (TYPE_MONSTER | TYPE_SPELL | TYPE_TRAP)) == (TYPE_TRAP | TYPE_MONSTER))
				lua_pushinteger(L, TYPE_MONSTER);
			else lua_pushinteger(L, (ch->triggering_effect->card_type & (TYPE_MONSTER | TYPE_SPELL | TYPE_TRAP)));
			break;
		case CHAININFO_EXTTYPE:
			lua_pushinteger(L, ch->triggering_effect->card_type);
			break;
		default:
			lua_pushnil(L);
			break;
		}
	}
	return args;
}
int32_t scriptlib::duel_get_chain_event(lua_State *L) {
	check_param_count(L, 1);
	uint32_t count = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(count);
	if(!ch)
		return 0;
	interpreter::group2value(L, ch->evt.event_cards);
	lua_pushinteger(L, ch->evt.event_player);
	lua_pushinteger(L, ch->evt.event_value);
	interpreter::effect2value(L, ch->evt.reason_effect);
	lua_pushinteger(L, ch->evt.reason);
	lua_pushinteger(L, ch->evt.reason_player);
	return 6;
}
int32_t scriptlib::duel_get_first_target(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(0);
	if(!ch || !ch->target_cards || ch->target_cards->container.size() == 0)
		return 0;
	for(auto& pcard : ch->target_cards->container)
		interpreter::card2value(L, pcard);
	return (int32_t)ch->target_cards->container.size();
}
int32_t scriptlib::duel_get_targets_relate_to_chain(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	chain* ch = pduel->game_field->get_chain(0);
	if(ch && ch->target_cards && ch->target_cards->container.size() > 0) {
		for(auto& pcard : ch->target_cards->container) {
			if(pcard->is_has_relation(*ch)) {
				pgroup->container.insert(pcard);
			}
		}
	}
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_is_phase(lua_State *L) {
	check_param_count(L, 1);
	uint32_t pphase = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->infos.phase == pphase)
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_is_main_phase(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint16_t phase = pduel->game_field->infos.phase;
	if(phase == PHASE_MAIN1 || phase == PHASE_MAIN2)
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_is_battle_phase(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint16_t phase = pduel->game_field->infos.phase;
	if((phase >= PHASE_BATTLE_START) && (phase <= PHASE_BATTLE))
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_get_current_phase(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->infos.phase);
	return 1;
}
int32_t scriptlib::duel_skip_phase(lua_State *L) {
	check_param_count(L, 4);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t phase = (uint32_t)lua_tointeger(L, 2);
	uint32_t reset = (uint32_t)lua_tointeger(L, 3);
	int32_t count = (int32_t)lua_tointeger(L, 4);
	int32_t value = (int32_t)lua_tointeger(L, 5);
	if(count <= 0)
		count = 1;
	duel* pduel = interpreter::get_duel_info(L);
	int32_t code = 0;
	if(phase == PHASE_DRAW)
		code = EFFECT_SKIP_DP;
	else if(phase == PHASE_STANDBY)
		code = EFFECT_SKIP_SP;
	else if(phase == PHASE_MAIN1)
		code = EFFECT_SKIP_M1;
	else if(phase == PHASE_BATTLE)
		code = EFFECT_SKIP_BP;
	else if(phase == PHASE_MAIN2)
		code = EFFECT_SKIP_M2;
	else if(phase == PHASE_END)
		code = EFFECT_SKIP_EP;
	else
		return 0;
	effect* peffect = pduel->new_effect();
	peffect->owner = pduel->game_field->temp_card;
	peffect->effect_owner = playerid;
	peffect->type = EFFECT_TYPE_FIELD;
	peffect->code = code;
	peffect->reset_flag = (reset & 0x3ff) | RESET_PHASE | RESET_SELF_TURN;
	peffect->flag[0] = EFFECT_FLAG_CANNOT_DISABLE | EFFECT_FLAG_PLAYER_TARGET;
	peffect->s_range = 1;
	peffect->o_range = 0;
	peffect->reset_count = count;
	peffect->value = value;
	pduel->game_field->add_effect(peffect, playerid);
	return 0;
}
int32_t scriptlib::duel_is_damage_calculated(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->core.damage_calculated);
	return 1;
}
int32_t scriptlib::duel_get_attacker(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	card* pcard = pduel->game_field->core.attacker;
	interpreter::card2value(L, pcard);
	return 1;
}
int32_t scriptlib::duel_get_attack_target(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	card* pcard = pduel->game_field->core.attack_target;
	interpreter::card2value(L, pcard);
	return 1;
}
int32_t scriptlib::duel_get_battle_monster(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_INT, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	card* attacker = pduel->game_field->core.attacker;
	card* defender = pduel->game_field->core.attack_target;
	for(int32_t i = 0; i < 2; i++) {
		if(attacker && attacker->current.controler == playerid)
			interpreter::card2value(L, attacker);
		else if(defender && defender->current.controler == playerid)
			interpreter::card2value(L, defender);
		else
			lua_pushnil(L);
		playerid = 1 - playerid;
	}
	return 2;
}
int32_t scriptlib::duel_disable_attack(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_ATTACK_DISABLE, 0, 0, 0, 0, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_chain_attack(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	card* attacker = pduel->game_field->core.attacker;
	if(!attacker || !attacker->is_affect_by_effect(pduel->game_field->core.reason_effect)) {
		return 0;
	}
	pduel->game_field->core.chain_attack = TRUE;
	pduel->game_field->core.chain_attacker_id = attacker->fieldid;
	if(lua_gettop(L) > 0) {
		check_param(L, PARAM_TYPE_CARD, 1);
		pduel->game_field->core.chain_attack_target = *(card**) lua_touserdata(L, 1);
	}
	return 0;
}
int32_t scriptlib::duel_readjust(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	card* adjcard = pduel->game_field->core.reason_effect->get_handler();
	++pduel->game_field->core.readjust_map[adjcard];
	if(pduel->game_field->core.readjust_map[adjcard] > 3) {
		pduel->game_field->send_to(adjcard, 0, REASON_RULE, pduel->game_field->core.reason_player, PLAYER_NONE, LOCATION_GRAVE, 0, POS_FACEUP);
		return lua_yield(L, 0);
	}
	pduel->game_field->core.re_adjust = TRUE;
	return 0;
}
int32_t scriptlib::duel_adjust_instantly(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) > 0) {
		check_param(L, PARAM_TYPE_CARD, 1);
		card* pcard = *(card**) lua_touserdata(L, 1);
		pcard->filter_disable_related_cards();
	}
	pduel->game_field->adjust_instant();
	return 0;
}
int32_t scriptlib::duel_adjust_all(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->adjust_all();
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		return 0;
	});
}
/**
 * \brief Duel.GetFieldGroup
 * \param playerid, location1, location2
 * \return Group
 */
int32_t scriptlib::duel_get_field_group(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 2);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_field_card(playerid, location1, location2, pgroup);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
 * \brief Duel.GetFieldGroupCount
 * \param playerid, location1, location2
 * \return Integer
 */
int32_t scriptlib::duel_get_field_group_count(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 2);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t count = pduel->game_field->filter_field_card(playerid, location1, location2, 0);
	lua_pushinteger(L, count);
	return 1;
}
/**
 * \brief Duel.GetDeckTop
 * \param playerid, count
 * \return Group
 */
int32_t scriptlib::duel_get_decktop_group(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	auto cit = pduel->game_field->player[playerid].list_main.rbegin();
	for(uint32_t i = 0; i < count && cit != pduel->game_field->player[playerid].list_main.rend(); ++i, ++cit)
		pgroup->container.insert(*cit);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
 * \brief Duel.GetExtraTopGroup
 * \param playerid, count
 * \return Group
 */
int32_t scriptlib::duel_get_extratop_group(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	auto cit = pduel->game_field->player[playerid].list_extra.rbegin() + pduel->game_field->player[playerid].extra_p_count;
	for(uint32_t i = 0; i < count && cit != pduel->game_field->player[playerid].list_extra.rend(); ++i, ++cit)
		pgroup->container.insert(*cit);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
* \brief Duel.GetMatchingGroup
* \param filter_func, self, location1, location2, exception card, (extraargs...)
* \return Group
*/
int32_t scriptlib::duel_get_matching_group(lua_State *L) {
	check_param_count(L, 5);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 5, TRUE))
		pexception = *(card**) lua_touserdata(L, 5);
	else if(check_param(L, PARAM_TYPE_GROUP, 5, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 5);
	uint32_t extraargs = lua_gettop(L) - 5;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, pgroup, pexception, pexgroup, extraargs);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
* \brief Duel.GetMatchingGroupCount
* \param filter_func, self, location1, location2, exception card, (extraargs...)
* \return Integer
*/
int32_t scriptlib::duel_get_matching_count(lua_State *L) {
	check_param_count(L, 5);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 5, TRUE))
		pexception = *(card**) lua_touserdata(L, 5);
	else if(check_param(L, PARAM_TYPE_GROUP, 5, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 5);
	uint32_t extraargs = lua_gettop(L) - 5;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, pgroup, pexception, pexgroup, extraargs);
	uint32_t count = (uint32_t)pgroup->container.size();
	lua_pushinteger(L, count);
	return 1;
}
/**
* \brief Duel.GetFirstMatchingCard
* \param filter_func, self, location1, location2, exception card, (extraargs...)
* \return Card | nil
*/
int32_t scriptlib::duel_get_first_matching_card(lua_State *L) {
	check_param_count(L, 5);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 5, TRUE))
		pexception = *(card**) lua_touserdata(L, 5);
	else if(check_param(L, PARAM_TYPE_GROUP, 5, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 5);
	uint32_t extraargs = lua_gettop(L) - 5;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	card* pret = nullptr;
	pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, 0, pexception, pexgroup, extraargs, &pret);
	if(pret)
		interpreter::card2value(L, pret);
	else lua_pushnil(L);
	return 1;
}
/**
* \brief Duel.IsExistingMatchingCard
* \param filter_func, self, location1, location2, count, exception card, (extraargs...)
* \return boolean
*/
int32_t scriptlib::duel_is_existing_matching_card(lua_State *L) {
	check_param_count(L, 6);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 6, TRUE))
		pexception = *(card**) lua_touserdata(L, 6);
	else if(check_param(L, PARAM_TYPE_GROUP, 6, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 6);
	uint32_t extraargs = lua_gettop(L) - 6;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	uint32_t fcount = (uint32_t)lua_tointeger(L, 5);
	lua_pushboolean(L, pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, 0, pexception, pexgroup, extraargs, nullptr, fcount));
	return 1;
}
/**
* \brief Duel.SelectMatchingCards
* \param playerid, filter_func, self, location1, location2, min, max, exception card, (extraargs...)
* \return Group
*/
int32_t scriptlib::duel_select_matching_cards(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 8);
	if(!lua_isnil(L, 2))
		check_param(L, PARAM_TYPE_FUNCTION, 2);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 8, TRUE))
		pexception = *(card**) lua_touserdata(L, 8);
	else if(check_param(L, PARAM_TYPE_GROUP, 8, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 8);
	uint32_t extraargs = lua_gettop(L) - 8;
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 3);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 4);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 5);
	uint32_t min = (uint32_t)lua_tointeger(L, 6);
	uint32_t max = (uint32_t)lua_tointeger(L, 7);
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_matching_card(L, 2, (uint8_t)self, location1, location2, pgroup, pexception, pexgroup, extraargs);
	pduel->game_field->core.select_cards.assign(pgroup->container.begin(), pgroup->container.end());
	pduel->game_field->add_process(PROCESSOR_SELECT_CARD, 0, 0, 0, playerid, min + (max << 16));
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		group* pgroup = pduel->new_group();
		for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i) {
			card* pcard = pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]];
			pgroup->container.insert(pcard);
		}
		interpreter::group2value(L, pgroup);
		return 1;
	});
}
/**
* \brief Duel.GetReleaseGroup
* \param playerid
* \return Group
*/
int32_t scriptlib::duel_get_release_group(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t hand = FALSE;
	if(lua_gettop(L) > 1)
		hand = lua_toboolean(L, 2);
	uint32_t reason = REASON_COST;
	if (lua_gettop(L) >= 3)
		reason = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_release_list(L, playerid, &pgroup->container, &pgroup->container, &pgroup->container, FALSE, hand, 0, 0, nullptr, nullptr, reason);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
* \brief Duel.GetReleaseGroupCount
* \param playerid
* \return Integer
*/
int32_t scriptlib::duel_get_release_group_count(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t hand = FALSE;
	if(lua_gettop(L) > 1)
		hand = lua_toboolean(L, 2);
	uint32_t reason = REASON_COST;
	if (lua_gettop(L) >= 3)
		reason = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->get_release_list(L, playerid, nullptr, nullptr, nullptr, FALSE, hand, 0, 0, nullptr, nullptr, reason));
	return 1;
}
int32_t scriptlib::duel_check_release_group(lua_State *L) {
	const int32_t must_param = 4;
	check_param_count(L, must_param);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t use_con = FALSE;
	if(!lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_FUNCTION, 2);
		use_con = TRUE;
	}
	uint32_t fcount = (uint32_t)lua_tointeger(L, 3);
	card* pexception = nullptr;
	group *pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 4, TRUE))
		pexception = *(card**) lua_touserdata(L, 4);
	else if(check_param(L, PARAM_TYPE_GROUP, 4, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 4);
	uint32_t extraargs = lua_gettop(L) - must_param;
	duel* pduel = interpreter::get_duel_info(L);
	int32_t result = pduel->game_field->check_release_list(L, playerid, fcount, use_con, FALSE, 2, extraargs, pexception, pexgroup, REASON_COST);
	pduel->game_field->core.must_select_cards.clear();
	lua_pushboolean(L, result);
	return 1;
}
int32_t scriptlib::duel_select_release_group(lua_State *L) {
	const int32_t must_param = 5;
	check_action_permission(L);
	check_param_count(L, must_param);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t use_con = FALSE;
	if(!lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_FUNCTION, 2);
		use_con = TRUE;
	}
	uint32_t min = (uint32_t)lua_tointeger(L, 3);
	uint32_t max = (uint32_t)lua_tointeger(L, 4);
	card* pexception = 0;
	group* pexgroup = 0;
	if(check_param(L, PARAM_TYPE_CARD, 5, TRUE))
		pexception = *(card**) lua_touserdata(L, 5);
	else if(check_param(L, PARAM_TYPE_GROUP, 5, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 5);
	uint32_t extraargs = lua_gettop(L) - must_param;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.release_cards.clear();
	pduel->game_field->core.release_cards_ex.clear();
	pduel->game_field->core.release_cards_ex_oneof.clear();
	pduel->game_field->get_release_list(L, playerid, &pduel->game_field->core.release_cards, &pduel->game_field->core.release_cards_ex, &pduel->game_field->core.release_cards_ex_oneof, use_con, FALSE, 2, extraargs, pexception, pexgroup, REASON_COST);
	pduel->game_field->add_process(PROCESSOR_SELECT_RELEASE, 0, 0, 0, playerid, (max << 16) + min);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		group* pgroup = pduel->new_group();
		for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i) {
			card* pcard = pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]];
			pgroup->container.insert(pcard);
		}
		interpreter::group2value(L, pgroup);
		return 1;
	});
}
int32_t scriptlib::duel_check_release_group_ex(lua_State *L) {
	const int32_t must_param = 6;
	check_param_count(L, must_param);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t use_con = FALSE;
	if(!lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_FUNCTION, 2);
		use_con = TRUE;
	}
	uint32_t fcount = (uint32_t)lua_tointeger(L, 3);
	uint32_t reason = (uint32_t)lua_tointeger(L, 4);
	int32_t use_hand = lua_toboolean(L, 5);
	card* pexception = nullptr;
	group *pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 6, TRUE))
		pexception = *(card**) lua_touserdata(L, 6);
	else if(check_param(L, PARAM_TYPE_GROUP, 6, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 6);
	uint32_t extraargs = lua_gettop(L) - must_param;
	duel* pduel = interpreter::get_duel_info(L);
	int32_t result = pduel->game_field->check_release_list(L, playerid, fcount, use_con, use_hand, 2, extraargs, pexception, pexgroup, reason);
	pduel->game_field->core.must_select_cards.clear();
	lua_pushboolean(L, result);
	return 1;
}
int32_t scriptlib::duel_select_release_group_ex(lua_State *L) {
	const int32_t must_param = 7;
	check_action_permission(L);
	check_param_count(L, must_param);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t use_con = FALSE;
	if(!lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_FUNCTION, 2);
		use_con = TRUE;
	}
	uint32_t min = (uint32_t)lua_tointeger(L, 3);
	uint32_t max = (uint32_t)lua_tointeger(L, 4);
	uint32_t reason = (uint32_t)lua_tointeger(L, 5);
	int32_t use_hand = lua_toboolean(L, 6);
	card* pexception = 0;
	group* pexgroup = 0;
	if(check_param(L, PARAM_TYPE_CARD, 7, TRUE))
		pexception = *(card**) lua_touserdata(L, 7);
	else if(check_param(L, PARAM_TYPE_GROUP, 7, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 7);
	uint32_t extraargs = lua_gettop(L) - must_param;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.release_cards.clear();
	pduel->game_field->core.release_cards_ex.clear();
	pduel->game_field->core.release_cards_ex_oneof.clear();
	pduel->game_field->get_release_list(L, playerid, &pduel->game_field->core.release_cards, &pduel->game_field->core.release_cards_ex, &pduel->game_field->core.release_cards_ex_oneof, use_con, use_hand, 2, extraargs, pexception, pexgroup, reason);
	pduel->game_field->add_process(PROCESSOR_SELECT_RELEASE, 0, 0, 0, playerid, (max << 16) + min);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		group* pgroup = pduel->new_group();
		for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i) {
			card* pcard = pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]];
			pgroup->container.insert(pcard);
		}
		interpreter::group2value(L, pgroup);
		return 1;
	});
}
/**
* \brief Duel.GetTributeGroup
* \param targetcard
* \return Group
*/
int32_t scriptlib::duel_get_tribute_group(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* target = *(card**) lua_touserdata(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_summon_release_list(target, &(pgroup->container), &(pgroup->container), nullptr);
	interpreter::group2value(L, pgroup);
	return 1;
}
/**
* \brief Duel.GetTributeCount
* \param targetcard
* \return Integer
*/
int32_t scriptlib::duel_get_tribute_count(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* target = *(card**) lua_touserdata(L, 1);
	group* mg = nullptr;
	if(lua_gettop(L) >= 2 && !lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_GROUP, 2);
		mg = *(group**) lua_touserdata(L, 2);
	}
	uint32_t ex = 0;
	if(lua_gettop(L) >= 3)
		ex = lua_toboolean(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->get_summon_release_list(target, nullptr, nullptr, nullptr, mg, ex));
	return 1;
}
int32_t scriptlib::duel_check_tribute(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* target = *(card**) lua_touserdata(L, 1);
	uint32_t min = (uint32_t)lua_tointeger(L, 2);
	uint32_t max = min;
	if(lua_gettop(L) >= 3 && !lua_isnil(L, 3))
		max = (uint32_t)lua_tointeger(L, 3);
	group* mg = nullptr;
	if(lua_gettop(L) >= 4 && !lua_isnil(L, 4)) {
		check_param(L, PARAM_TYPE_GROUP, 4);
		mg = *(group**)lua_touserdata(L, 4);
	}
	uint8_t toplayer = target->current.controler;
	if(lua_gettop(L) >= 5 && !lua_isnil(L, 5))
		toplayer = (uint8_t)lua_tointeger(L, 5);
	duel* pduel = target->pduel;
	uint32_t zone = 0x1f;
	if(pduel->game_field->core.limit_extra_summon_zone)
		zone= pduel->game_field->core.limit_extra_summon_zone;
	if(lua_gettop(L) >= 6 && !lua_isnil(L, 6))
		zone = (uint32_t)lua_tointeger(L, 6);
	uint32_t releasable = 0xff00ff;
	if(pduel->game_field->core.limit_extra_summon_releasable)
		releasable = pduel->game_field->core.limit_extra_summon_releasable;
	lua_pushboolean(L, pduel->game_field->check_tribute(target, min, max, mg, toplayer, zone, releasable));
	return 1;
}
int32_t scriptlib::duel_select_tribute(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 4);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* target = *(card**) lua_touserdata(L, 2);
	uint32_t min = (uint32_t)lua_tointeger(L, 3);
	uint32_t max = (uint32_t)lua_tointeger(L, 4);
	group* mg = nullptr;
	if(lua_gettop(L) >= 5 && !lua_isnil(L, 5)) {
		check_param(L, PARAM_TYPE_GROUP, 5);
		mg = *(group**) lua_touserdata(L, 5);
	}
	uint8_t toplayer = playerid;
	if(lua_gettop(L) >= 6)
		toplayer = (uint8_t)lua_tointeger(L, 6);
	if(toplayer != 0 && toplayer != 1)
		return 0;
	uint32_t ex = FALSE;
	if(toplayer != playerid)
		ex = TRUE;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t zone = 0x1f;
	if(pduel->game_field->core.limit_extra_summon_zone)
		zone = pduel->game_field->core.limit_extra_summon_zone;
	uint32_t releasable = 0xff00ff;
	if(pduel->game_field->core.limit_extra_summon_releasable)
		releasable = pduel->game_field->core.limit_extra_summon_releasable;
	pduel->game_field->core.release_cards.clear();
	pduel->game_field->core.release_cards_ex.clear();
	pduel->game_field->core.release_cards_ex_oneof.clear();
	pduel->game_field->get_summon_release_list(target, &pduel->game_field->core.release_cards, &pduel->game_field->core.release_cards_ex, &pduel->game_field->core.release_cards_ex_oneof, mg, ex, releasable);
	pduel->game_field->select_tribute_cards(0, playerid, 0, min, max, toplayer, zone);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		group* pgroup = pduel->new_group();
		for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i) {
			card* pcard = pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]];
			pgroup->container.insert(pcard);
		}
		interpreter::group2value(L, pgroup);
		return 1;
	});
}
/**
* \brief Duel.GetTargetCount
* \param filter_func, self, location1, location2, exception card, (extraargs...)
* \return Group
*/
int32_t scriptlib::duel_get_target_count(lua_State *L) {
	check_param_count(L, 5);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 5, TRUE))
		pexception = *(card**) lua_touserdata(L, 5);
	else if(check_param(L, PARAM_TYPE_GROUP, 5, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 5);
	uint32_t extraargs = lua_gettop(L) - 5;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	group* pgroup = pduel->new_group();
	uint32_t count = 0;
	pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, pgroup, pexception, pexgroup, extraargs, nullptr, 0, TRUE);
	count = (uint32_t)pgroup->container.size();
	lua_pushinteger(L, count);
	return 1;
}
/**
* \brief Duel.IsExistingTarget
* \param filter_func, self, location1, location2, count, exception card, (extraargs...)
* \return boolean
*/
int32_t scriptlib::duel_is_existing_target(lua_State *L) {
	check_param_count(L, 6);
	if(!lua_isnil(L, 1))
		check_param(L, PARAM_TYPE_FUNCTION, 1);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 6, TRUE))
		pexception = *(card**) lua_touserdata(L, 6);
	else if(check_param(L, PARAM_TYPE_GROUP, 6, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 6);
	uint32_t extraargs = lua_gettop(L) - 6;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	uint32_t count = (uint32_t)lua_tointeger(L, 5);
	lua_pushboolean(L, pduel->game_field->filter_matching_card(L, 1, (uint8_t)self, location1, location2, 0, pexception, pexgroup, extraargs, nullptr, count, TRUE));
	return 1;
}
/**
* \brief Duel.SelectTarget
* \param playerid, filter_func, self, location1, location2, min, max, exception card, (extraargs...)
* \return Group
*/
int32_t scriptlib::duel_select_target(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 8);
	if(!lua_isnil(L, 2))
		check_param(L, PARAM_TYPE_FUNCTION, 2);
	card* pexception = nullptr;
	group* pexgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 8, TRUE))
		pexception = *(card**) lua_touserdata(L, 8);
	else if(check_param(L, PARAM_TYPE_GROUP, 8, TRUE))
		pexgroup = *(group**) lua_touserdata(L, 8);
	uint32_t extraargs = lua_gettop(L) - 8;
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t self = (uint32_t)lua_tointeger(L, 3);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 4);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 5);
	uint32_t min = (uint32_t)lua_tointeger(L, 6);
	uint32_t max = (uint32_t)lua_tointeger(L, 7);
	if(pduel->game_field->core.current_chain.size() == 0)
		return 0;
	group* pgroup = pduel->new_group();
	pduel->game_field->filter_matching_card(L, 2, (uint8_t)self, location1, location2, pgroup, pexception, pexgroup, extraargs, nullptr, 0, TRUE);
	pduel->game_field->core.select_cards.assign(pgroup->container.begin(), pgroup->container.end());
	pduel->game_field->add_process(PROCESSOR_SELECT_CARD, 0, 0, 0, playerid, min + (max << 16));
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		chain* ch = pduel->game_field->get_chain(0);
		if(!ch)
			return 0;
		if(!ch->target_cards) {
			ch->target_cards = pduel->new_group();
			ch->target_cards->is_readonly = GTYPE_READ_ONLY;
		}
		group* tg = ch->target_cards;
		effect* peffect = ch->triggering_effect;
		if(peffect->type & EFFECT_TYPE_CONTINUOUS) {
			for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i)
				tg->container.insert(pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]]);
			interpreter::group2value(L, tg);
		} else {
			group* pgroup = pduel->new_group();
			for(int32_t i = 0; i < pduel->game_field->returns.bvalue[0]; ++i) {
				card* pcard = pduel->game_field->core.select_cards[pduel->game_field->returns.bvalue[i + 1]];
				tg->container.insert(pcard);
				pgroup->container.insert(pcard);
				pcard->create_relation(*ch);
				if(peffect->is_flag(EFFECT_FLAG_CARD_TARGET)) {
					pduel->write_buffer8(MSG_BECOME_TARGET);
					pduel->write_buffer8(1);
					pduel->write_buffer32(pcard->get_info_location());
				}
			}
			interpreter::group2value(L, pgroup);
		}
		return 1;
	});
}
int32_t scriptlib::duel_get_must_material(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t limit = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_must_material_list(playerid, limit, &pgroup->container);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_check_must_material(lua_State *L) {
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* mcard = nullptr;
	group* mgroup = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
		mcard = *(card**)lua_touserdata(L, 2);
		pduel = mcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
		mgroup = *(group**)lua_touserdata(L, 2);
		pduel = mgroup->pduel;
	} else
		pduel = interpreter::get_duel_info(L);
	if(mgroup) {
		pgroup = pduel->new_group(mgroup->container);
		pgroup->is_readonly = GTYPE_READ_ONLY;
	} else if(mcard) {
		pgroup = pduel->new_group(mcard);
		pgroup->is_readonly = GTYPE_READ_ONLY;
	} else
		pgroup = 0;
	uint32_t limit = (uint32_t)lua_tointeger(L, 3);
	lua_pushboolean(L, pduel->game_field->check_must_material(pgroup, playerid, limit));
	return 1;
}
int32_t scriptlib::duel_select_fusion_material(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	check_param(L, PARAM_TYPE_CARD, 2);
	check_param(L, PARAM_TYPE_GROUP, 3);
	card* cg = nullptr;
	uint32_t chkf = PLAYER_NONE;
	uint8_t not_material = FALSE;
	if(lua_gettop(L) > 3 && !lua_isnil(L, 4)) {
		check_param(L, PARAM_TYPE_CARD, 4);
		cg = *(card**) lua_touserdata(L, 4);
	}
	if(lua_gettop(L) > 4)
		chkf = (uint32_t)lua_tointeger(L, 5);
	if(lua_gettop(L) > 5)
		not_material = lua_toboolean(L, 6);
	card* pcard = *(card**) lua_touserdata(L, 2);
	group* pgroup = *(group**) lua_touserdata(L, 3);
	pcard->fusion_select(playerid, pgroup, cg, chkf, not_material);
	duel* pduel = pcard->pduel;
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		group* pgroup = pduel->new_group(pduel->game_field->core.fusion_materials);
		if(lua_gettop(L) > 3 && !lua_isnil(L, 4)) {
			card* cg = *(card**)lua_touserdata(L, 4);
			pgroup->container.insert(cg);
		}
		interpreter::group2value(L, pgroup);
		return 1;
	});
}
int32_t scriptlib::duel_set_fusion_material(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_GROUP, 1);
	group* pgroup = *(group**) lua_touserdata(L, 1);
	duel* pduel = pgroup->pduel;
	pduel->game_field->core.fusion_materials = pgroup->container;
	return 0;
}
int32_t scriptlib::duel_set_synchro_material(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_GROUP, 1);
	group* pgroup = *(group**) lua_touserdata(L, 1);
	duel* pduel = pgroup->pduel;
	pduel->game_field->core.synchro_materials = pgroup->container;
	return 0;
}
int32_t scriptlib::duel_get_synchro_material(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t facedown = FALSE;
	if (lua_gettop(L) >= 2)
		facedown = lua_toboolean(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	card_set mats;
	pduel->game_field->get_synchro_material(playerid, &mats);
	group* pgroup = pduel->new_group();
	for (auto cit = mats.begin(); cit != mats.end(); ++cit) {
		card* pcard = *cit;
		if (pcard->current.location == LOCATION_MZONE && !pcard->is_position(POS_FACEUP) && !facedown)
			continue;
		pgroup->container.insert(*cit);
	}
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_select_synchro_material(lua_State *L) {
	check_param_count(L, 6);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**) lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(!lua_isnil(L, 3))
		check_param(L, PARAM_TYPE_FUNCTION, 3);
	if(!lua_isnil(L, 4))
		check_param(L, PARAM_TYPE_FUNCTION, 4);
	int32_t min = (int32_t)lua_tointeger(L, 5);
	int32_t max = (int32_t)lua_tointeger(L, 6);
	card* smat = nullptr;
	group* mg = nullptr;
	if(lua_gettop(L) >= 7 && !lua_isnil(L, 7)) {
		check_param(L, PARAM_TYPE_CARD, 7);
		smat = *(card**) lua_touserdata(L, 7);
	}
	if(lua_gettop(L) >= 8 && !lua_isnil(L, 8)) {
		check_param(L, PARAM_TYPE_GROUP, 8);
		mg = *(group**) lua_touserdata(L, 8);
	}
	auto filter1 = interpreter::get_function_handle(L, 3);
	auto filter2 = interpreter::get_function_handle(L, 4);
	card_set select_cards;
	if (mg) {
		for (auto& pm : mg->container) {
			if (pduel->game_field->check_tuner_material(L, pcard, pm, 3, 4, min, max, nullptr, mg))
				select_cards.insert(pm);
		}
		pduel->game_field->core.select_cards.assign(select_cards.begin(), select_cards.end());
		pduel->game_field->add_process(PROCESSOR_SELECT_SYNCHRO, 0, nullptr, nullptr, playerid, min + (max << 16), filter1, filter2, pcard, mg);
	}
	else {
		card_set material;
		pduel->game_field->get_synchro_material(playerid, &material);
		for (auto& tuner : material) {
			if (pduel->game_field->check_tuner_material(L, pcard, tuner, 3, 4, min, max, smat, nullptr))
				select_cards.insert(tuner);
		}
		pduel->game_field->core.select_cards.assign(select_cards.begin(), select_cards.end());
		pduel->game_field->add_process(PROCESSOR_SELECT_SYNCHRO, 0, nullptr, nullptr, playerid + 0x10000, min + (max << 16), filter1, filter2, pcard, smat);
	}
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_check_synchro_material(lua_State *L) {
	check_param_count(L, 5);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* pcard = *(card**) lua_touserdata(L, 1);
	duel* pduel = pcard->pduel;
	if(!lua_isnil(L, 2))
		check_param(L, PARAM_TYPE_FUNCTION, 2);
	if(!lua_isnil(L, 3))
		check_param(L, PARAM_TYPE_FUNCTION, 3);
	int32_t min = (int32_t)lua_tointeger(L, 4);
	int32_t max = (int32_t)lua_tointeger(L, 5);
	card* smat = nullptr;
	group* mg = nullptr;
	if(lua_gettop(L) >= 6 && !lua_isnil(L, 6)) {
		check_param(L, PARAM_TYPE_CARD, 6);
		smat = *(card**) lua_touserdata(L, 6);
	}
	if(lua_gettop(L) >= 7 && !lua_isnil(L, 7)) {
		check_param(L, PARAM_TYPE_GROUP, 7);
		mg = *(group**) lua_touserdata(L, 7);
	}
	lua_pushboolean(L, pduel->game_field->check_synchro_material(L, pcard, 2, 3, min, max, smat, mg));
	return 1;
}
int32_t scriptlib::duel_select_tuner_material(lua_State *L) {
	check_param_count(L, 7);
	check_param(L, PARAM_TYPE_CARD, 2);
	check_param(L, PARAM_TYPE_CARD, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**) lua_touserdata(L, 2);
	card* tuner = *(card**) lua_touserdata(L, 3);
	duel* pduel = pcard->pduel;
	if(!lua_isnil(L, 4))
		check_param(L, PARAM_TYPE_FUNCTION, 4);
	if(!lua_isnil(L, 5))
		check_param(L, PARAM_TYPE_FUNCTION, 5);
	int32_t min = (int32_t)lua_tointeger(L, 6);
	int32_t max = (int32_t)lua_tointeger(L, 7);
	group* mg = nullptr;
	if(lua_gettop(L) >= 8 && !lua_isnil(L, 8)) {
		check_param(L, PARAM_TYPE_GROUP, 8);
		mg = *(group**) lua_touserdata(L, 8);
	}
	if(!pduel->game_field->check_tuner_material(L, pcard, tuner, 4, 5, min, max, nullptr, mg))
		return 0;
	pduel->game_field->core.select_cards.clear();
	pduel->game_field->core.select_cards.push_back(tuner);
	pduel->game_field->returns.bvalue[1] = 0;
	auto filter1 = interpreter::get_function_handle(L, 4);
	auto filter2 = interpreter::get_function_handle(L, 5);
	pduel->game_field->add_process(PROCESSOR_SELECT_SYNCHRO, 1, nullptr, nullptr, playerid, min + (max << 16), filter1, filter2, pcard, mg);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_check_tuner_material(lua_State *L) {
	check_param_count(L, 6);
	check_param(L, PARAM_TYPE_CARD, 1);
	check_param(L, PARAM_TYPE_CARD, 2);
	card* pcard = *(card**) lua_touserdata(L, 1);
	card* tuner = *(card**) lua_touserdata(L, 2);
	duel* pduel = pcard->pduel;
	if(!lua_isnil(L, 3))
		check_param(L, PARAM_TYPE_FUNCTION, 3);
	if(!lua_isnil(L, 4))
		check_param(L, PARAM_TYPE_FUNCTION, 4);
	int32_t min = (int32_t)lua_tointeger(L, 5);
	int32_t max = (int32_t)lua_tointeger(L, 6);
	group* mg = nullptr;
	if(lua_gettop(L) >= 7 && !lua_isnil(L, 7)) {
		check_param(L, PARAM_TYPE_GROUP, 7);
		mg = *(group**) lua_touserdata(L, 7);
	}
	lua_pushboolean(L, pduel->game_field->check_tuner_material(L, pcard, tuner, 3, 4, min, max, nullptr, mg));
	return 1;
}
int32_t scriptlib::duel_get_ritual_material(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_ritual_material(playerid, pduel->game_field->core.reason_effect, &pgroup->container);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_get_ritual_material_ex(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_ritual_material(playerid, pduel->game_field->core.reason_effect, &pgroup->container, TRUE);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_release_ritual_material(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_GROUP, 1);
	group* pgroup = *(group**) lua_touserdata(L, 1);
	pgroup->pduel->game_field->ritual_release(pgroup->container);
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_get_fusion_material(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t location = LOCATION_HAND + LOCATION_MZONE;
	if(lua_gettop(L) >= 2)
		location = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroupall = pduel->new_group();
	group* pgroupbase = pduel->new_group();
	pduel->game_field->get_fusion_material(playerid, &pgroupall->container, &pgroupbase->container, location);
	interpreter::group2value(L, pgroupall);
	interpreter::group2value(L, pgroupbase);
	return 2;
}
int32_t scriptlib::duel_is_summon_cancelable(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->core.summon_cancelable);
	return 1;
}
int32_t scriptlib::duel_set_must_select_cards(lua_State *L) {
	check_param_count(L, 1);
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		card* pcard = *(card**) lua_touserdata(L, 1);
		duel* pduel = pcard->pduel;
		pduel->game_field->core.must_select_cards.clear();
		pduel->game_field->core.must_select_cards.push_back(pcard);
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		group* pgroup = *(group**) lua_touserdata(L, 1);
		duel* pduel = pgroup->pduel;
		pduel->game_field->core.must_select_cards.assign(pgroup->container.begin(), pgroup->container.end());
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	return 0;
}
int32_t scriptlib::duel_grab_must_select_cards(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	if(pduel->game_field->core.must_select_cards.size())
		pgroup->container.insert(pduel->game_field->core.must_select_cards.begin(), pduel->game_field->core.must_select_cards.end());
	pduel->game_field->core.must_select_cards.clear();
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_set_target_card(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	duel* pduel = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 1, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 1);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 1, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 1);
		pduel = pgroup->pduel;
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 1);
	chain* ch = pduel->game_field->get_chain(0);
	if(!ch)
		return 0;
	if(!ch->target_cards) {
		ch->target_cards = pduel->new_group();
		ch->target_cards->is_readonly = GTYPE_READ_ONLY;
	}
	group* targets = ch->target_cards;
	effect* peffect = ch->triggering_effect;
	if(peffect->type & EFFECT_TYPE_CONTINUOUS) {
		if(pcard)
			targets->container.insert(pcard);
		else
			targets->container = pgroup->container;
	} else {
		if(pcard) {
			targets->container.insert(pcard);
			pcard->create_relation(*ch);
		} else {
			targets->container.insert(pgroup->container.begin(), pgroup->container.end());
			for(auto& group_card : pgroup->container)
				group_card->create_relation(*ch);
		}
		if(peffect->is_flag(EFFECT_FLAG_CARD_TARGET)) {
			if(pcard) {
				pduel->write_buffer8(MSG_BECOME_TARGET);
				pduel->write_buffer8(1);
				pduel->write_buffer32(pcard->get_info_location());
			} else {
				for(auto& group_card : pgroup->container) {
					pduel->write_buffer8(MSG_BECOME_TARGET);
					pduel->write_buffer8(1);
					pduel->write_buffer32(group_card->get_info_location());
				}
			}
		}
	}
	return 0;
}
int32_t scriptlib::duel_clear_target_card(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(0);
	if(ch && ch->target_cards)
		ch->target_cards->container.clear();
	return 0;
}
int32_t scriptlib::duel_set_target_player(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(0);
	if(ch)
		ch->target_player = playerid;
	return 0;
}
int32_t scriptlib::duel_set_target_param(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	uint32_t param = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(0);
	if(ch)
		ch->target_param = param;
	return 0;
}
/**
* \brief Duel.SetOperationInfo
* \param target_group, target_count, target_player, targ
* \return N/A
*/
int32_t scriptlib::duel_set_operation_info(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 6);
	group* pgroup = nullptr;
	card* pcard = nullptr;
	group* pg = nullptr;
	uint32_t ct = (uint32_t)lua_tointeger(L, 1);
	uint32_t cate = (uint32_t)lua_tointeger(L, 2);
	uint32_t count = (uint32_t)lua_tointeger(L, 4);
	int32_t playerid = (int32_t)lua_tointeger(L, 5);
	uint32_t param = (uint32_t)lua_tointeger(L, 6);
	duel* pduel;
	if(check_param(L, PARAM_TYPE_CARD, 3, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 3);
		pduel = pcard->pduel;
	} else if(check_param(L, PARAM_TYPE_GROUP, 3, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 3);
		pduel = pgroup->pduel;
	} else
		pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(ct);
	if(!ch)
		return 0;
	if(pgroup) {
		pg = pduel->new_group(pgroup->container);
		pg->is_readonly = GTYPE_READ_ONLY;
	} else if(pcard) {
		pg = pduel->new_group(pcard);
		pg->is_readonly = GTYPE_READ_ONLY;
	} else
		pg = nullptr;
	optarget opt;
	opt.op_cards = pg;
	opt.op_count = count;
	opt.op_player = playerid;
	opt.op_param = param;
	auto omit = ch->opinfos.find(cate);
	if(omit != ch->opinfos.end() && omit->second.op_cards)
		pduel->delete_group(omit->second.op_cards);
	ch->opinfos[cate] = opt;
	return 0;
}
int32_t scriptlib::duel_get_operation_info(lua_State *L) {
	check_param_count(L, 2);
	uint32_t ct = (uint32_t)lua_tointeger(L, 1);
	uint32_t cate = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(ct);
	if(!ch)
		return 0;
	auto oit = ch->opinfos.find(cate);
	if(oit != ch->opinfos.end()) {
		optarget& opt = oit->second;
		lua_pushboolean(L, 1);
		if(opt.op_cards)
			interpreter::group2value(L, opt.op_cards);
		else
			lua_pushnil(L);
		lua_pushinteger(L, opt.op_count);
		lua_pushinteger(L, opt.op_player);
		lua_pushinteger(L, opt.op_param);
		return 5;
	} else {
		lua_pushboolean(L, 0);
		return 1;
	}
}
int32_t scriptlib::duel_get_operation_count(lua_State *L) {
	check_param_count(L, 1);
	uint32_t ct = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(ct);
	if(!ch)
		return 0;
	lua_pushinteger(L, ch->opinfos.size());
	return 1;
}
int32_t scriptlib::duel_clear_operation_info(lua_State* L) {
	check_action_permission(L);
	check_param_count(L, 1);
	uint32_t ct = (uint32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	chain* ch = pduel->game_field->get_chain(ct);
	if(!ch)
		return 0;
	for(auto& oit : ch->opinfos) {
		if(oit.second.op_cards)
			pduel->delete_group(oit.second.op_cards);
	}
	ch->opinfos.clear();
	return 0;
}
int32_t scriptlib::duel_check_xyz_material(lua_State *L) {
	check_param_count(L, 6);
	check_param(L, PARAM_TYPE_CARD, 1);
	uint32_t findex = 0;
	if(!lua_isnil(L, 2)) {
		check_param(L, PARAM_TYPE_FUNCTION, 2);
		findex = 2;
	}
	card* scard = *(card**) lua_touserdata(L, 1);
	uint32_t lv = (uint32_t)lua_tointeger(L, 3);
	uint32_t minc = (uint32_t)lua_tointeger(L, 4);
	uint32_t maxc = (uint32_t)lua_tointeger(L, 5);
	group* mg = nullptr;
	if(!lua_isnil(L, 6)) {
		check_param(L, PARAM_TYPE_GROUP, 6);
		mg = *(group**) lua_touserdata(L, 6);
	}
	lua_pushboolean(L, scard->pduel->game_field->check_xyz_material(L, scard, findex, lv, minc, maxc, mg));
	return 1;
}
int32_t scriptlib::duel_select_xyz_material(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 6);
	check_param(L, PARAM_TYPE_CARD, 2);
	uint32_t findex = 0;
	if(!lua_isnil(L, 3)) {
		check_param(L, PARAM_TYPE_FUNCTION, 3);
		findex = 3;
	}
	card* scard = *(card**) lua_touserdata(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	uint32_t lv = (uint32_t)lua_tointeger(L, 4);
	uint32_t minc = (uint32_t)lua_tointeger(L, 5);
	uint32_t maxc = (uint32_t)lua_tointeger(L, 6);
	group* mg = nullptr;
	if(lua_gettop(L) >= 7 && !lua_isnil(L, 7)) {
		check_param(L, PARAM_TYPE_GROUP, 7);
		mg = *(group**) lua_touserdata(L, 7);
	}
	duel* pduel = scard->pduel;
	if(!pduel->game_field->check_xyz_material(L, scard, findex, lv, minc, maxc, mg))
		return 0;
	pduel->game_field->get_xyz_material(L, scard, findex, lv, maxc, mg);
	scard->pduel->game_field->add_process(PROCESSOR_SELECT_XMATERIAL, 0, 0, (group*)scard, playerid + (lv << 16), minc + (maxc << 16));
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_overlay(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	card* target = *(card**) lua_touserdata(L, 1);
	card* pcard = nullptr;
	group* pgroup = nullptr;
	if(check_param(L, PARAM_TYPE_CARD, 2, TRUE)) {
		pcard = *(card**) lua_touserdata(L, 2);
	} else if(check_param(L, PARAM_TYPE_GROUP, 2, TRUE)) {
		pgroup = *(group**) lua_touserdata(L, 2);
	} else
		return luaL_error(L, "Parameter %d should be \"Card\" or \"Group\".", 2);
	if(pcard) {
		card_set cset{ pcard };
		target->xyz_overlay(cset);
	} else
		target->xyz_overlay(pgroup->container);
	uint32_t adjust = TRUE;
	if(lua_gettop(L) > 2) {
		adjust = lua_toboolean(L, 3);
	}
	if(adjust)
		target->pduel->game_field->adjust_all();
	return lua_yield(L, 0);
}
int32_t scriptlib::duel_get_overlay_group(lua_State *L) {
	check_param_count(L, 3);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	group* pgroup = pduel->new_group();
	pduel->game_field->get_overlay_group(rplayer, s, o, &pgroup->container);
	interpreter::group2value(L, pgroup);
	return 1;
}
int32_t scriptlib::duel_get_overlay_count(lua_State *L) {
	check_param_count(L, 3);
	uint32_t rplayer = (uint32_t)lua_tointeger(L, 1);
	if(rplayer != 0 && rplayer != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->get_overlay_count(rplayer, s, o));
	return 1;
}
int32_t scriptlib::duel_check_remove_overlay_card(lua_State *L) {
	check_param_count(L, 5);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	int32_t count = (int32_t)lua_tointeger(L, 4);
	int32_t reason = (int32_t)lua_tointeger(L, 5);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_remove_overlay_card(playerid, 0, s, o, count, reason));
	return 1;
}
int32_t scriptlib::duel_remove_overlay_card(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 6);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t s = (uint32_t)lua_tointeger(L, 2);
	uint32_t o = (uint32_t)lua_tointeger(L, 3);
	int32_t min = (int32_t)lua_tointeger(L, 4);
	int32_t max = (int32_t)lua_tointeger(L, 5);
	int32_t reason = (int32_t)lua_tointeger(L, 6);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->remove_overlay_card(reason, 0, playerid, s, o, min, max);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_hint(lua_State * L) {
	check_param_count(L, 3);
	int32_t htype = (int32_t)lua_tointeger(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t desc = (int32_t)lua_tointeger(L, 3);
	if(htype == HINT_OPSELECTED)
		playerid = 1 - playerid;
	duel* pduel = interpreter::get_duel_info(L);
	if(htype == HINT_SELECTMSG)
		pduel->game_field->core.last_select_hint[playerid] = desc;
	pduel->write_buffer8(MSG_HINT);
	pduel->write_buffer8(htype);
	pduel->write_buffer8(playerid);
	pduel->write_buffer32(desc);
	return 0;
}
int32_t scriptlib::duel_get_last_select_hint(lua_State* L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint8_t playerid = pduel->game_field->core.reason_player;
	if(lua_gettop(L) >= 1)
		playerid = (uint8_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	lua_pushinteger(L, pduel->game_field->core.last_select_hint[playerid]);
	return 1;
}
int32_t scriptlib::duel_hint_selection(lua_State *L) {
	check_param_count(L, 1);
	check_param(L, PARAM_TYPE_GROUP, 1);
	group* pgroup = *(group**) lua_touserdata(L, 1);
	duel* pduel = pgroup->pduel;
	for(auto& pcard : pgroup->container) {
		pduel->write_buffer8(MSG_BECOME_TARGET);
		pduel->write_buffer8(1);
		pduel->write_buffer32(pcard->get_info_location());
	}
	return 0;
}
int32_t scriptlib::duel_select_effect_yesno(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	card* pcard = *(card**) lua_touserdata(L, 2);
	int32_t desc = 95;
	if(lua_gettop(L) >= 3)
		desc = (int32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_SELECT_EFFECTYN, 0, 0, (group*)pcard, playerid, desc);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_select_yesno(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	int32_t desc = (int32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_SELECT_YESNO, 0, 0, 0, playerid, desc);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushboolean(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_select_option(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 1);
	uint32_t count = lua_gettop(L) - 1;
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.select_options.clear();
	for(uint32_t i = 0; i < count; ++i)
		pduel->game_field->core.select_options.push_back((uint32_t)lua_tointeger(L, i + 2));
	pduel->game_field->add_process(PROCESSOR_SELECT_OPTION, 0, 0, 0, playerid, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		pduel->write_buffer8(MSG_HINT);
		pduel->write_buffer8(HINT_OPSELECTED);
		pduel->write_buffer8(playerid);
		pduel->write_buffer32(pduel->game_field->core.select_options[pduel->game_field->returns.ivalue[0]]);
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_select_sequence(lua_State * L) {
	check_action_permission(L);
	return 0;
}
int32_t scriptlib::duel_select_position(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	card* pcard = *(card**) lua_touserdata(L, 2);
	uint32_t positions = (uint32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_SELECT_POSITION, 0, 0, 0, playerid + (positions << 16), pcard->data.code);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_select_disable_field(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 5);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	uint32_t filter = (uint32_t)lua_tointeger(L, 5);
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t ct1 = 0, ct2 = 0, ct3 = 0, ct4 = 0, plist = 0, flag = 0xffffffff;
	if(location1 & LOCATION_MZONE) {
		ct1 = pduel->game_field->get_useable_count(nullptr, playerid, LOCATION_MZONE, PLAYER_NONE, 0, 0xff, &plist);
		flag = (flag & 0xffffff00) | plist;
	}
	if(location1 & LOCATION_SZONE) {
		ct2 = pduel->game_field->get_useable_count(nullptr, playerid, LOCATION_SZONE, PLAYER_NONE, 0, 0xff, &plist);
		flag = (flag & 0xffff00ff) | (plist << 8);
	}
	if(location2 & LOCATION_MZONE) {
		ct3 = pduel->game_field->get_useable_count(nullptr, 1 - playerid, LOCATION_MZONE, PLAYER_NONE, 0, 0xff, &plist);
		flag = (flag & 0xff00ffff) | (plist << 16);
	}
	if(location2 & LOCATION_SZONE) {
		ct4 = pduel->game_field->get_useable_count(nullptr, 1 - playerid, LOCATION_SZONE, PLAYER_NONE, 0, 0xff, &plist);
		flag = (flag & 0xffffff) | (plist << 24);
	}
	if((location1 & LOCATION_MZONE) && (location2 & LOCATION_MZONE) && pduel->game_field->core.duel_rule >= NEW_MASTER_RULE) {
		if(pduel->game_field->is_location_useable(playerid, LOCATION_MZONE, 5)) {
			flag &= ~(0x1U << 5);
			ct1 += 1;
		}
		if(pduel->game_field->is_location_useable(playerid, LOCATION_MZONE, 6)) {
			flag &= ~(0x1U << 6);
			ct1 += 1;
		}
	}
	flag |= filter | 0xe080e080;
	if(count > ct1 + ct2 + ct3 + ct4)
		count = ct1 + ct2 + ct3 + ct4;
	if(count == 0)
		return 0;
	pduel->game_field->add_process(PROCESSOR_SELECT_DISFIELD, 0, 0, 0, playerid, flag, count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		uint32_t count = (uint32_t)lua_tointeger(L, 2);
		int32_t dfflag = 0;
		uint8_t pa = 0;
		for(uint32_t i = 0; i < count; ++i) {
			uint8_t p = pduel->game_field->returns.bvalue[pa];
			uint8_t l = pduel->game_field->returns.bvalue[pa + 1];
			uint8_t s = pduel->game_field->returns.bvalue[pa + 2];
			dfflag |= 0x1U << (s + (p == playerid ? 0 : 16) + (l == LOCATION_MZONE ? 0 : 8));
			pa += 3;
		}
		if(dfflag & (0x1U << 5))
			dfflag |= 0x1U << (16 + 6);
		if(dfflag & (0x1U << 6))
			dfflag |= 0x1U << (16 + 5);
		lua_pushinteger(L, dfflag);
		return 1;
	});
}
/*
Select zones from the viewpoint of playerid.
flag	selectable zones XX000000 X0000000 for each player, 1 byte for SZONE(6), 1 byte for MZONE(7)
filter	excluding zones, 0 for selectable, 1 for unselectable
*/
int32_t scriptlib::duel_select_field(lua_State* L) {
	check_action_permission(L);
	check_param_count(L, 5);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	uint32_t count = (uint32_t)lua_tointeger(L, 2);
	uint32_t location1 = (uint32_t)lua_tointeger(L, 3);
	uint32_t location2 = (uint32_t)lua_tointeger(L, 4);
	uint32_t filter = (uint32_t)lua_tointeger(L, 5);
	uint16_t type = PROCESSOR_SELECT_DISFIELD;
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) >= 6) {
		type = PROCESSOR_SELECT_PLACE;
		uint32_t code = (uint32_t)lua_tointeger(L, 6);
		pduel->write_buffer8(MSG_HINT);
		pduel->write_buffer8(HINT_SELECTMSG);
		pduel->write_buffer8(playerid);
		pduel->write_buffer32(code);
	}
	uint32_t flag = 0xffffffff;
	if(location1 & LOCATION_MZONE) {
		flag &= 0xffffff80;
	}
	if(location1 & LOCATION_SZONE) {
		flag &= (pduel->game_field->core.duel_rule == 3) ? 0xffff00ff : 0xffffc0ff;
	}
	if(location2 & LOCATION_MZONE) {
		flag &= 0xff80ffff;
	}
	if(location2 & LOCATION_SZONE) {
		flag &= (pduel->game_field->core.duel_rule == 3) ? 0x00ffffff : 0xc0ffffff;
	}
	if((location1 & LOCATION_MZONE) && (location2 & LOCATION_MZONE) && pduel->game_field->core.duel_rule >= NEW_MASTER_RULE) {
		flag &= 0xffffff9f;
	}
	flag |= filter | 0x00800080;
	pduel->game_field->add_process(type, 0, 0, 0, playerid, flag, count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State* L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		uint32_t count = (uint32_t)lua_tointeger(L, 2);
		int32_t dfflag = 0;
		uint8_t pa = 0;
		for(uint32_t i = 0; i < count; ++i) {
			uint8_t p = pduel->game_field->returns.bvalue[pa];
			uint8_t l = pduel->game_field->returns.bvalue[pa + 1];
			uint8_t s = pduel->game_field->returns.bvalue[pa + 2];
			dfflag |= 0x1u << (s + (p == playerid ? 0 : 16) + (l == LOCATION_MZONE ? 0 : 8));
			pa += 3;
		}
		if(dfflag & (0x1U << 5))
			dfflag |= 0x1U << (16 + 6);
		if(dfflag & (0x1U << 6))
			dfflag |= 0x1U << (16 + 5);
		lua_pushinteger(L, dfflag);
		return 1;
		});
}
int32_t scriptlib::duel_announce_race(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	int32_t available = (int32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_ANNOUNCE_RACE, 0, 0, 0, playerid + (count << 16), available);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_announce_attribute(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 3);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	int32_t available = (int32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->add_process(PROCESSOR_ANNOUNCE_ATTRIB, 0, 0, 0, playerid + (count << 16), available);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_announce_level(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t min = 1;
	int32_t max = 12;
	if(lua_gettop(L) >= 2 && !lua_isnil(L, 2))
		min = (int32_t)lua_tointeger(L, 2);
	if(lua_gettop(L) >= 3 && !lua_isnil(L, 3))
		max = (int32_t)lua_tointeger(L, 3);
	if(min > max) {
		int32_t aux = max;
		max = min;
		min = aux;
	}
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.select_options.clear();
	int32_t count = 0;
	if(lua_gettop(L) > 3) {
		for(int32_t i = min; i <= max; ++i) {
			int32_t chk = 1;
			for(int32_t j = 4; j <= lua_gettop(L); ++j) {
				if (!lua_isnil(L, j) && i == lua_tointeger(L, j)) {
					chk = 0;
					break;
				}
			}
			if(chk) {
				count += 1;
				pduel->game_field->core.select_options.push_back(i);
			}
		}
	} else {
		for(int32_t i = min; i <= max; ++i) {
			count += 1;
			pduel->game_field->core.select_options.push_back(i);
		}
	}
	if(count == 0)
		return 0;
	pduel->game_field->add_process(PROCESSOR_ANNOUNCE_NUMBER, 0, 0, 0, playerid + 0x10000, 0xc0001);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->core.select_options[pduel->game_field->returns.ivalue[0]]);
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 2;
	});
}
int32_t scriptlib::duel_announce_card(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.select_options.clear();
	if(lua_gettop(L) == 1) {
		pduel->game_field->core.select_options.push_back(TRUE);
	} else {
		for(int32_t i = 2; i <= lua_gettop(L); ++i)
			pduel->game_field->core.select_options.push_back((uint32_t)lua_tointeger(L, i));
	}
	int32_t stack_size = 0;
	for(auto& it : pduel->game_field->core.select_options) {
		switch(it) {
		case OPCODE_ADD:
		case OPCODE_SUB:
		case OPCODE_MUL:
		case OPCODE_DIV:
		case OPCODE_AND:
		case OPCODE_OR:
			stack_size -= 1;
			break;
		case OPCODE_NEG:
		case OPCODE_NOT:
		case OPCODE_ISCODE:
		case OPCODE_ISSETCARD:
		case OPCODE_ISTYPE:
		case OPCODE_ISRACE:
		case OPCODE_ISATTRIBUTE:
			break;
		default:
			stack_size += 1;
			break;
		}
		if(stack_size <= 0)
			break;
	}
	if(stack_size != 1)
		return luaL_error(L, "Parameters are invalid.");
	pduel->game_field->add_process(PROCESSOR_ANNOUNCE_CARD, 0, 0, 0, playerid, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_announce_type(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	pduel->game_field->core.select_options.clear();
	pduel->game_field->core.select_options.push_back(70);
	pduel->game_field->core.select_options.push_back(71);
	pduel->game_field->core.select_options.push_back(72);
	pduel->game_field->add_process(PROCESSOR_SELECT_OPTION, 0, 0, 0, playerid, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		pduel->write_buffer8(MSG_HINT);
		pduel->write_buffer8(HINT_OPSELECTED);
		pduel->write_buffer8(playerid);
		pduel->write_buffer32(pduel->game_field->core.select_options[pduel->game_field->returns.ivalue[0]]);
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_announce_number(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->core.select_options.clear();
	for(int32_t i = 2; i <= lua_gettop(L); ++i)
		pduel->game_field->core.select_options.push_back((uint32_t)lua_tointeger(L, i));
	pduel->game_field->add_process(PROCESSOR_ANNOUNCE_NUMBER, 0, 0, 0, playerid, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->core.select_options[pduel->game_field->returns.ivalue[0]]);
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 2;
	});
}
int32_t scriptlib::duel_announce_coin(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	pduel->game_field->core.select_options.clear();
	pduel->game_field->core.select_options.push_back(60);
	pduel->game_field->core.select_options.push_back(61);
	pduel->game_field->add_process(PROCESSOR_SELECT_OPTION, 0, 0, 0, playerid, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t playerid = (int32_t)lua_tointeger(L, 1);
		pduel->write_buffer8(MSG_HINT);
		pduel->write_buffer8(HINT_OPSELECTED);
		pduel->write_buffer8(playerid);
		pduel->write_buffer32(pduel->game_field->core.select_options[pduel->game_field->returns.ivalue[0]]);
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_toss_coin(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	if((playerid != 0 && playerid != 1) || count == 0 || count < -1)
		return 0;
	if(count > MAX_COIN_COUNT)
		count = MAX_COIN_COUNT;
	pduel->game_field->add_process(PROCESSOR_TOSS_COIN, 0, pduel->game_field->core.reason_effect, 0, (pduel->game_field->core.reason_player << 16) + playerid, count);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t count = pduel->game_field->core.coin_count;
		for(int32_t i = 0; i < count; ++i)
			lua_pushinteger(L, pduel->game_field->core.coin_result[i]);
		return count;
	});
}
int32_t scriptlib::duel_toss_dice(lua_State * L) {
	check_action_permission(L);
	check_param_count(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count1 = (int32_t)lua_tointeger(L, 2);
	int32_t count2 = 0;
	if(lua_gettop(L) > 2)
		count2 = (int32_t)lua_tointeger(L, 3);
	if((playerid != 0 && playerid != 1) || count1 <= 0 || count2 < 0)
		return 0;
	if(count1 > 5)
		count1 = 5;
	if(count2 > 5 - count1)
		count2 = 5 - count1;
	pduel->game_field->add_process(PROCESSOR_TOSS_DICE, 0, pduel->game_field->core.reason_effect, 0, (pduel->game_field->core.reason_player << 16) + playerid, count1 + (count2 << 16));
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		int32_t count1 = (int32_t)lua_tointeger(L, 2);
		int32_t count2 = 0;
		if(lua_gettop(L) > 2)
			count2 = (int32_t)lua_tointeger(L, 3);
		for(int32_t i = 0; i < count1 + count2; ++i)
			lua_pushinteger(L, pduel->game_field->core.dice_result[i]);
		return count1 + count2;
	});
}
int32_t scriptlib::duel_rock_paper_scissors(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	uint8_t repeat = TRUE;
	if (lua_gettop(L) > 0)
		repeat = lua_toboolean(L, 1);
	pduel->game_field->add_process(PROCESSOR_ROCK_PAPER_SCISSORS, 0, 0, 0, repeat, 0);
	return lua_yieldk(L, 0, (lua_KContext)pduel, [](lua_State *L, int32_t status, lua_KContext ctx) {
		duel* pduel = (duel*)ctx;
		lua_pushinteger(L, pduel->game_field->returns.ivalue[0]);
		return 1;
	});
}
int32_t scriptlib::duel_get_coin_result(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	for(int32_t i = 0; i < pduel->game_field->core.coin_count; ++i)
		lua_pushinteger(L, pduel->game_field->core.coin_result[i]);
	return pduel->game_field->core.coin_count;
}
int32_t scriptlib::duel_get_dice_result(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	for(int32_t i = 0; i < 5; ++i)
		lua_pushinteger(L, pduel->game_field->core.dice_result[i]);
	return 5;
}
int32_t scriptlib::duel_set_coin_result(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	int32_t res;
	for(int32_t i = 0; i < MAX_COIN_COUNT; ++i) {
		res = (int32_t)lua_tointeger(L, i + 1);
		if(res != 0 && res != 1)
			res = 0;
		pduel->game_field->core.coin_result[i] = res;
	}
	return 0;
}
int32_t scriptlib::duel_set_dice_result(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	int32_t res;
	for(int32_t i = 0; i < 5; ++i) {
		res = (int32_t)lua_tointeger(L, i + 1);
		if(res < 1 || res > 255)
			res = 1;
		pduel->game_field->core.dice_result[i] = res;
	}
	return 0;
}
int32_t scriptlib::duel_is_player_affected_by_effect(lua_State *L) {
	check_param_count(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushnil(L);
		return 1;
	}
	int32_t code = (int32_t)lua_tointeger(L, 2);
	effect_set eset;
	pduel->game_field->filter_player_effect(playerid, code, &eset);
	int32_t size = 0;
	for(effect_set::size_type i = 0; i < eset.size(); ++i) {
		if(eset[i]->check_count_limit(playerid)) {
			interpreter::effect2value(L, eset[i]);
			++size;
		}
	}
	if(!size) {
		lua_pushnil(L);
		return 1;
	}
	return size;
}
int32_t scriptlib::duel_is_player_can_draw(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	uint32_t count = 0;
	if(lua_gettop(L) > 1)
		count = (uint32_t)lua_tointeger(L, 2);
	duel* pduel = interpreter::get_duel_info(L);
	if(count == 0)
		lua_pushboolean(L, pduel->game_field->is_player_can_draw(playerid));
	else
		lua_pushboolean(L, (int)(pduel->game_field->is_player_can_draw(playerid) && (pduel->game_field->player[playerid].list_main.size() >= count)));
	return 1;
}
int32_t scriptlib::duel_is_player_can_discard_deck(lua_State * L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_discard_deck(playerid, count));
	return 1;
}
int32_t scriptlib::duel_is_player_can_discard_deck_as_cost(lua_State * L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_discard_deck_as_cost(playerid, count));
	return 1;
}
int32_t scriptlib::duel_is_player_can_summon(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_SUMMON));
	else {
		check_param_count(L, 3);
		check_param(L, PARAM_TYPE_CARD, 3);
		int32_t sumtype = (int32_t)lua_tointeger(L, 2);
		card* pcard = *(card**) lua_touserdata(L, 3);
		lua_pushboolean(L, pduel->game_field->is_player_can_summon(sumtype, playerid, pcard, playerid));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_mset(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_MSET));
	else {
		check_param_count(L, 3);
		check_param(L, PARAM_TYPE_CARD, 3);
		int32_t sumtype = (int32_t)lua_tointeger(L, 2);
		card* pcard = *(card**) lua_touserdata(L, 3);
		lua_pushboolean(L, pduel->game_field->is_player_can_mset(sumtype, playerid, pcard, playerid));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_sset(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_SSET));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		lua_pushboolean(L, pduel->game_field->is_player_can_sset(playerid, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_spsummon(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_spsummon(playerid));
	else {
		check_param_count(L, 5);
		check_param(L, PARAM_TYPE_CARD, 5);
		int32_t sumtype = (int32_t)lua_tointeger(L, 2);
		int32_t sumpos = (int32_t)lua_tointeger(L, 3);
		int32_t toplayer = (int32_t)lua_tointeger(L, 4);
		card* pcard = *(card**) lua_touserdata(L, 5);
		lua_pushboolean(L, pduel->game_field->is_player_can_spsummon(pduel->game_field->core.reason_effect, sumtype, sumpos, playerid, toplayer, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_flipsummon(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_FLIP_SUMMON));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		lua_pushboolean(L, pduel->game_field->is_player_can_flipsummon(playerid, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_spsummon_monster(lua_State * L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	int32_t code = (int32_t)lua_tointeger(L, 2);
	card_data dat;
	::read_card(code, &dat);
	dat.code = code;
	dat.alias = 0;
	if(lua_gettop(L) >= 3 && !lua_isnil(L, 3))
		write_setcode(dat.setcode, lua_tointeger(L, 3));
	if(lua_gettop(L) >= 4 && !lua_isnil(L, 4))
		dat.type = (uint32_t)lua_tointeger(L, 4);
	if(lua_gettop(L) >= 5 && !lua_isnil(L, 5))
		dat.attack = (int32_t)lua_tointeger(L, 5);
	if(lua_gettop(L) >= 6 && !lua_isnil(L, 6))
		dat.defense = (int32_t)lua_tointeger(L, 6);
	if(lua_gettop(L) >= 7 && !lua_isnil(L, 7))
		dat.level = (uint32_t)lua_tointeger(L, 7);
	if(lua_gettop(L) >= 8 && !lua_isnil(L, 8))
		dat.race = (uint32_t)lua_tointeger(L, 8);
	if(lua_gettop(L) >= 9 && !lua_isnil(L, 9))
		dat.attribute = (uint32_t)lua_tointeger(L, 9);
	int32_t pos = POS_FACEUP;
	int32_t toplayer = playerid;
	uint32_t sumtype = 0;
	if(lua_gettop(L) >= 10)
		pos = (int32_t)lua_tointeger(L, 10);
	if(lua_gettop(L) >= 11)
		toplayer = (int32_t)lua_tointeger(L, 11);
	if(lua_gettop(L) >= 12)
		sumtype = (uint32_t)lua_tointeger(L, 12);
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_spsummon_monster(playerid, toplayer, pos, sumtype, &dat));
	return 1;
}
int32_t scriptlib::duel_is_player_can_spsummon_count(lua_State * L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	int32_t count = (int32_t)lua_tointeger(L, 2);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_player_can_spsummon_count(playerid, count));
	return 1;
}
int32_t scriptlib::duel_is_player_can_release(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_RELEASE));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		uint32_t reason = REASON_COST;
		if (lua_gettop(L) >= 3)
			reason = (uint32_t)lua_tointeger(L, 3);
		lua_pushboolean(L, pduel->game_field->is_player_can_release(playerid, pcard, reason));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_remove(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_REMOVE));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		uint32_t reason = REASON_EFFECT;
		if(lua_gettop(L) >= 3)
			reason = (uint32_t)lua_tointeger(L, 3);
		lua_pushboolean(L, pduel->game_field->is_player_can_remove(playerid, pcard, reason));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_send_to_hand(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_TO_HAND));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		lua_pushboolean(L, pduel->game_field->is_player_can_send_to_hand(playerid, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_send_to_grave(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_TO_GRAVE));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		lua_pushboolean(L, pduel->game_field->is_player_can_send_to_grave(playerid, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_send_to_deck(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(lua_gettop(L) == 1)
		lua_pushboolean(L, pduel->game_field->is_player_can_action(playerid, EFFECT_CANNOT_TO_DECK));
	else {
		check_param_count(L, 2);
		check_param(L, PARAM_TYPE_CARD, 2);
		card* pcard = *(card**) lua_touserdata(L, 2);
		lua_pushboolean(L, pduel->game_field->is_player_can_send_to_deck(playerid, pcard));
	}
	return 1;
}
int32_t scriptlib::duel_is_player_can_additional_summon(lua_State * L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1) {
		lua_pushboolean(L, 0);
		return 1;
	}
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->core.extra_summon[playerid] == 0)
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_is_chain_solving(lua_State * L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->core.chain_solving);
	return 1;
}
int32_t scriptlib::duel_is_chain_negatable(lua_State * L) {
	check_param_count(L, 1);
	lua_pushboolean(L, 1);
	return 1;
}
int32_t scriptlib::duel_is_chain_disablable(lua_State * L) {
	check_param_count(L, 1);
	int32_t chaincount = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->core.chain_solving) {
		lua_pushboolean(L, pduel->game_field->is_chain_disablable(chaincount));
		return 1;
	}
	lua_pushboolean(L, 1);
	return 1;
}
int32_t scriptlib::duel_is_chain_disabled(lua_State* L) {
	check_param_count(L, 1);
	int32_t chaincount = (int32_t)lua_tointeger(L, 1);
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->core.chain_solving) {
		lua_pushboolean(L, pduel->game_field->is_chain_disabled(chaincount));
		return 1;
	}
	lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_check_chain_target(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 2);
	int32_t chaincount = (int32_t)lua_tointeger(L, 1);
	card* pcard = *(card**) lua_touserdata(L, 2);
	lua_pushboolean(L, pcard->pduel->game_field->check_chain_target(chaincount, pcard));
	return 1;
}
int32_t scriptlib::duel_check_chain_uniqueness(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	if(pduel->game_field->core.current_chain.size() == 0) {
		lua_pushboolean(L, 1);
		return 1;
	}
	std::set<uint32_t> er;
	for(const auto& ch : pduel->game_field->core.current_chain)
		er.insert(ch.triggering_effect->get_handler()->get_code());
	if(er.size() == pduel->game_field->core.current_chain.size())
		lua_pushboolean(L, 1);
	else
		lua_pushboolean(L, 0);
	return 1;
}
int32_t scriptlib::duel_get_activity_count(lua_State *L) {
	check_param_count(L, 2);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	int32_t retct = lua_gettop(L) - 1;
	for(int32_t i = 0; i < retct; ++i) {
		int32_t activity_type = (int32_t)lua_tointeger(L, 2 + i);
		switch(activity_type) {
			case 1:
				lua_pushinteger(L, pduel->game_field->core.summon_state_count[playerid]);
				break;
			case 2:
				lua_pushinteger(L, pduel->game_field->core.normalsummon_state_count[playerid]);
				break;
			case 3:
				lua_pushinteger(L, pduel->game_field->core.spsummon_state_count[playerid]);
				break;
			case 4:
				lua_pushinteger(L, pduel->game_field->core.flipsummon_state_count[playerid]);
				break;
			case 5:
				lua_pushinteger(L, pduel->game_field->core.attack_state_count[playerid]);
				break;
			case 6:
				lua_pushinteger(L, pduel->game_field->core.battle_phase_count[playerid]);
				break;
			default:
				lua_pushinteger(L, 0);
				break;
		}
	}
	return retct;
}
int32_t scriptlib::duel_check_phase_activity(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->core.phase_action);
	return 1;
}
int32_t scriptlib::duel_add_custom_activity_counter(lua_State *L) {
	check_param_count(L, 3);
	check_param(L, PARAM_TYPE_FUNCTION, 3);
	int32_t counter_id = (int32_t)lua_tointeger(L, 1);
	int32_t activity_type = (int32_t)lua_tointeger(L, 2);
	int32_t counter_filter = interpreter::get_function_handle(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	switch(activity_type) {
		case ACTIVITY_SUMMON: {
			pduel->game_field->core.summon_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		case ACTIVITY_NORMALSUMMON: {
			pduel->game_field->core.normalsummon_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		case ACTIVITY_SPSUMMON: {
			pduel->game_field->core.spsummon_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		case ACTIVITY_FLIPSUMMON: {
			pduel->game_field->core.flipsummon_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		case ACTIVITY_ATTACK: {
			pduel->game_field->core.attack_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		case ACTIVITY_BATTLE_PHASE:
			break;
		case ACTIVITY_CHAIN: {
			pduel->game_field->core.chain_counter.emplace(counter_id, activity_map::mapped_type(counter_filter, 0));
			break;
		}
		default:
			break;
	}
	return 0;
}
int32_t scriptlib::duel_get_custom_activity_count(lua_State *L) {
	check_param_count(L, 3);
	int32_t counter_id = (int32_t)lua_tointeger(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 2);
	int32_t activity_type = (int32_t)lua_tointeger(L, 3);
	duel* pduel = interpreter::get_duel_info(L);
	uint32_t val = 0;
	switch(activity_type) {
		case ACTIVITY_SUMMON: {
			auto iter = pduel->game_field->core.summon_counter.find(counter_id);
			if(iter != pduel->game_field->core.summon_counter.end())
				val = iter->second.second;
			break;
		}
		case ACTIVITY_NORMALSUMMON: {
			auto iter = pduel->game_field->core.normalsummon_counter.find(counter_id);
			if(iter != pduel->game_field->core.normalsummon_counter.end())
				val = iter->second.second;
			break;
		}
		case ACTIVITY_SPSUMMON: {
			auto iter = pduel->game_field->core.spsummon_counter.find(counter_id);
			if(iter != pduel->game_field->core.spsummon_counter.end())
				val = iter->second.second;
			break;
		}
		case ACTIVITY_FLIPSUMMON: {
			auto iter = pduel->game_field->core.flipsummon_counter.find(counter_id);
			if(iter != pduel->game_field->core.flipsummon_counter.end())
				val = iter->second.second;
			break;
		}
		case ACTIVITY_ATTACK: {
			auto iter = pduel->game_field->core.attack_counter.find(counter_id);
			if(iter != pduel->game_field->core.attack_counter.end())
				val = iter->second.second;
			break;
		}
		case ACTIVITY_BATTLE_PHASE:
			break;
		case ACTIVITY_CHAIN: {
			auto iter = pduel->game_field->core.chain_counter.find(counter_id);
			if(iter != pduel->game_field->core.chain_counter.end())
				val = iter->second.second;
			break;
		}
		default:
			break;
	}
	if(playerid == 0)
		lua_pushinteger(L, val & 0xffff);
	else
		lua_pushinteger(L, (val >> 16) & 0xffff);
	return 1;
}

int32_t scriptlib::duel_get_battled_count(lua_State *L) {
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushinteger(L, pduel->game_field->core.battled_count[playerid]);
	return 1;
}
int32_t scriptlib::duel_is_able_to_enter_bp(lua_State *L) {
	duel* pduel = interpreter::get_duel_info(L);
	lua_pushboolean(L, pduel->game_field->is_able_to_enter_bp());
	return 1;
}
int32_t scriptlib::duel_swap_deck_and_grave(lua_State *L) {
	check_action_permission(L);
	check_param_count(L, 1);
	int32_t playerid = (int32_t)lua_tointeger(L, 1);
	if(playerid != 0 && playerid != 1)
		return 0;
	duel* pduel = interpreter::get_duel_info(L);
	pduel->game_field->swap_deck_and_grave(playerid);
	return 0;
}
int32_t scriptlib::duel_majestic_copy(lua_State *L) {
	check_param_count(L, 2);
	check_param(L, PARAM_TYPE_CARD, 1);
	check_param(L, PARAM_TYPE_CARD, 2);
	card* pcard = *(card**) lua_touserdata(L, 1);
	card* copy_target = *(card**) lua_touserdata(L, 2);
	for(auto& peffect: copy_target->initial_effect) {
		if (!(peffect->type & (EFFECT_TYPES_CHAIN_LINK & ~EFFECT_TYPE_ACTIVATE)))
			continue;
		if (peffect->type & EFFECT_TYPE_XMATERIAL)
			continue;
		if (!peffect->is_monster_effect())
			continue;
		effect* ceffect = peffect->clone();
		ceffect->owner = pcard;
		ceffect->flag[0] &= ~EFFECT_FLAG_INITIAL;
		ceffect->effect_owner = PLAYER_NONE;
		ceffect->reset_flag = RESET_EVENT | RESETS_STANDARD | RESET_PHASE | PHASE_END | RESET_SELF_TURN | RESET_OPPO_TURN;
		ceffect->reset_count = 1;
		ceffect->recharge();
		if(ceffect->type & EFFECT_TYPE_TRIGGER_F) {
			ceffect->type &= ~EFFECT_TYPE_TRIGGER_F;
			ceffect->type |= EFFECT_TYPE_TRIGGER_O;
			ceffect->flag[0] |= EFFECT_FLAG_DELAY;
		}
		if(ceffect->type & EFFECT_TYPE_QUICK_F) {
			ceffect->type &= ~EFFECT_TYPE_QUICK_F;
			ceffect->type |= EFFECT_TYPE_QUICK_O;
		}
		pcard->add_effect(ceffect);
	}
	return 0;
}

static const struct luaL_Reg duellib[] = {
	#ifdef _IRR_ANDROID_PLATFORM_
	{ "LoadScript", scriptlib::duel_load_script },
	#endif
	
	{ "EnableGlobalFlag", scriptlib::duel_enable_global_flag },
	{ "GetLP", scriptlib::duel_get_lp },
	{ "SetLP", scriptlib::duel_set_lp },
	{ "IsTurnPlayer", scriptlib::duel_is_turn_player },
	{ "GetTurnPlayer", scriptlib::duel_get_turn_player },
	{ "GetTurnCount", scriptlib::duel_get_turn_count },
	{ "GetDrawCount", scriptlib::duel_get_draw_count },
	{ "RegisterEffect", scriptlib::duel_register_effect },
	{ "RegisterFlagEffect", scriptlib::duel_register_flag_effect },
	{ "GetFlagEffect", scriptlib::duel_get_flag_effect },
	{ "ResetFlagEffect", scriptlib::duel_reset_flag_effect },
	{ "SetFlagEffectLabel", scriptlib::duel_set_flag_effect_label },
	{ "GetFlagEffectLabel", scriptlib::duel_get_flag_effect_label },
	{ "Destroy", scriptlib::duel_destroy },
	{ "Remove", scriptlib::duel_remove },
	{ "SendtoGrave", scriptlib::duel_sendto_grave },
	{ "SendtoHand", scriptlib::duel_sendto_hand },
	{ "SendtoDeck", scriptlib::duel_sendto_deck },
	{ "SendtoExtraP", scriptlib::duel_sendto_extra },
	{ "GetOperatedGroup", scriptlib::duel_get_operated_group },
	{ "Summon", scriptlib::duel_summon },
	{ "SpecialSummonRule", scriptlib::duel_special_summon_rule },
	{ "SynchroSummon", scriptlib::duel_synchro_summon },
	{ "XyzSummon", scriptlib::duel_xyz_summon },
	{ "LinkSummon", scriptlib::duel_link_summon },
	{ "MSet", scriptlib::duel_setm },
	{ "SSet", scriptlib::duel_sets },
	{ "CreateToken", scriptlib::duel_create_token },
	{ "SpecialSummon", scriptlib::duel_special_summon },
	{ "SpecialSummonStep", scriptlib::duel_special_summon_step },
	{ "SpecialSummonComplete", scriptlib::duel_special_summon_complete },
	{ "IsCanAddCounter", scriptlib::duel_is_can_add_counter },
	{ "RemoveCounter", scriptlib::duel_remove_counter },
	{ "IsCanRemoveCounter", scriptlib::duel_is_can_remove_counter },
	{ "GetCounter", scriptlib::duel_get_counter },
	{ "ChangePosition", scriptlib::duel_change_form },
	{ "Release", scriptlib::duel_release },
	{ "MoveToField", scriptlib::duel_move_to_field },
	{ "ReturnToField", scriptlib::duel_return_to_field },
	{ "MoveSequence", scriptlib::duel_move_sequence },
	{ "SwapSequence", scriptlib::duel_swap_sequence },
	{ "Activate", scriptlib::duel_activate_effect },
	{ "SetChainLimit", scriptlib::duel_set_chain_limit },
	{ "SetChainLimitTillChainEnd", scriptlib::duel_set_chain_limit_p },
	{ "GetChainMaterial", scriptlib::duel_get_chain_material },
	{ "ConfirmDecktop", scriptlib::duel_confirm_decktop },
	{ "ConfirmExtratop", scriptlib::duel_confirm_extratop },
	{ "ConfirmCards", scriptlib::duel_confirm_cards },
	{ "SortDecktop", scriptlib::duel_sort_decktop },
	{ "CheckEvent", scriptlib::duel_check_event },
	{ "RaiseEvent", scriptlib::duel_raise_event },
	{ "RaiseSingleEvent", scriptlib::duel_raise_single_event },
	{ "CheckTiming", scriptlib::duel_check_timing },
	{ "IsEnvironment", scriptlib::duel_is_environment },
	{ "Win", scriptlib::duel_win },
	{ "Draw", scriptlib::duel_draw },
	{ "Damage", scriptlib::duel_damage },
	{ "Recover", scriptlib::duel_recover },
	{ "RDComplete", scriptlib::duel_rd_complete },
	{ "Equip", scriptlib::duel_equip },
	{ "EquipComplete", scriptlib::duel_equip_complete },
	{ "GetControl", scriptlib::duel_get_control },
	{ "SwapControl", scriptlib::duel_swap_control },
	{ "CheckLPCost", scriptlib::duel_check_lp_cost },
	{ "PayLPCost", scriptlib::duel_pay_lp_cost },
	{ "DiscardDeck", scriptlib::duel_discard_deck },
	{ "DiscardHand", scriptlib::duel_discard_hand },
	{ "DisableShuffleCheck", scriptlib::duel_disable_shuffle_check },
	{ "DisableSelfDestroyCheck", scriptlib::duel_disable_self_destroy_check },
	{ "RevealSelectDeckSequence", scriptlib::duel_reveal_select_deck_sequence },
	{ "ShuffleDeck", scriptlib::duel_shuffle_deck },
	{ "ShuffleExtra", scriptlib::duel_shuffle_extra },
	{ "ShuffleHand", scriptlib::duel_shuffle_hand },
	{ "ShuffleSetCard", scriptlib::duel_shuffle_setcard },
	{ "ChangeAttacker", scriptlib::duel_change_attacker },
	{ "ChangeAttackTarget", scriptlib::duel_change_attack_target },
	{ "CalculateDamage", scriptlib::duel_calculate_damage },
	{ "GetBattleDamage", scriptlib::duel_get_battle_damage },
	{ "ChangeBattleDamage", scriptlib::duel_change_battle_damage },
	{ "ChangeTargetCard", scriptlib::duel_change_target },
	{ "ChangeTargetPlayer", scriptlib::duel_change_target_player },
	{ "ChangeTargetParam", scriptlib::duel_change_target_param },
	{ "BreakEffect", scriptlib::duel_break_effect },
	{ "ChangeChainOperation", scriptlib::duel_change_effect },
	{ "NegateActivation", scriptlib::duel_negate_activate },
	{ "NegateEffect", scriptlib::duel_negate_effect },
	{ "NegateRelatedChain", scriptlib::duel_negate_related_chain },
	{ "NegateSummon", scriptlib::duel_disable_summon },
	{ "IncreaseSummonedCount", scriptlib::duel_increase_summon_count },
	{ "CheckSummonedCount", scriptlib::duel_check_summon_count },
	{ "GetLocationCount", scriptlib::duel_get_location_count },
	{ "GetMZoneCount", scriptlib::duel_get_mzone_count },
	{ "GetSZoneCount", scriptlib::duel_get_szone_count },
	{ "GetLocationCountFromEx", scriptlib::duel_get_location_count_fromex },
	{ "GetUsableMZoneCount", scriptlib::duel_get_usable_mzone_count },
	{ "GetLinkedGroup", scriptlib::duel_get_linked_group },
	{ "GetLinkedGroupCount", scriptlib::duel_get_linked_group_count },
	{ "GetLinkedZone", scriptlib::duel_get_linked_zone },
	{ "GetFieldCard", scriptlib::duel_get_field_card },
	{ "CheckLocation", scriptlib::duel_check_location },
	{ "GetCurrentChain", scriptlib::duel_get_current_chain },
	{ "GetReadyChain", scriptlib::duel_get_ready_chain },
	{ "GetChainInfo", scriptlib::duel_get_chain_info },
	{ "GetChainEvent", scriptlib::duel_get_chain_event },
	{ "GetFirstTarget", scriptlib::duel_get_first_target },
	{ "GetTargetsRelateToChain", scriptlib::duel_get_targets_relate_to_chain },
	{ "IsPhase", scriptlib::duel_is_phase },
	{ "IsMainPhase", scriptlib::duel_is_main_phase },
	{ "IsBattlePhase", scriptlib::duel_is_battle_phase },
	{ "GetCurrentPhase", scriptlib::duel_get_current_phase },
	{ "SkipPhase", scriptlib::duel_skip_phase },
	{ "IsDamageCalculated", scriptlib::duel_is_damage_calculated },
	{ "GetAttacker", scriptlib::duel_get_attacker },
	{ "GetAttackTarget", scriptlib::duel_get_attack_target },
	{ "GetBattleMonster", scriptlib::duel_get_battle_monster },
	{ "NegateAttack", scriptlib::duel_disable_attack },
	{ "ChainAttack", scriptlib::duel_chain_attack },
	{ "Readjust", scriptlib::duel_readjust },
	{ "AdjustInstantly", scriptlib::duel_adjust_instantly },
	{ "AdjustAll", scriptlib::duel_adjust_all },
	{ "GetFieldGroup", scriptlib::duel_get_field_group },
	{ "GetFieldGroupCount", scriptlib::duel_get_field_group_count },
	{ "GetDecktopGroup", scriptlib::duel_get_decktop_group },
	{ "GetExtraTopGroup", scriptlib::duel_get_extratop_group },
	{ "GetMatchingGroup", scriptlib::duel_get_matching_group },
	{ "GetMatchingGroupCount", scriptlib::duel_get_matching_count },
	{ "GetFirstMatchingCard", scriptlib::duel_get_first_matching_card },
	{ "IsExistingMatchingCard", scriptlib::duel_is_existing_matching_card },
	{ "SelectMatchingCard", scriptlib::duel_select_matching_cards },
	{ "GetReleaseGroup", scriptlib::duel_get_release_group },
	{ "GetReleaseGroupCount", scriptlib::duel_get_release_group_count },
	{ "CheckReleaseGroup", scriptlib::duel_check_release_group },
	{ "SelectReleaseGroup", scriptlib::duel_select_release_group },
	{ "CheckReleaseGroupEx", scriptlib::duel_check_release_group_ex },
	{ "SelectReleaseGroupEx", scriptlib::duel_select_release_group_ex },
	{ "GetTributeGroup", scriptlib::duel_get_tribute_group },
	{ "GetTributeCount", scriptlib::duel_get_tribute_count },
	{ "CheckTribute", scriptlib::duel_check_tribute },
	{ "SelectTribute", scriptlib::duel_select_tribute },
	{ "GetTargetCount", scriptlib::duel_get_target_count },
	{ "IsExistingTarget", scriptlib::duel_is_existing_target },
	{ "SelectTarget", scriptlib::duel_select_target },
	{ "GetMustMaterial", scriptlib::duel_get_must_material },
	{ "CheckMustMaterial", scriptlib::duel_check_must_material },
	{ "SelectFusionMaterial", scriptlib::duel_select_fusion_material },
	{ "SetFusionMaterial", scriptlib::duel_set_fusion_material },
	{ "SetSynchroMaterial", scriptlib::duel_set_synchro_material },
	{ "GetSynchroMaterial", scriptlib::duel_get_synchro_material },
	{ "SelectSynchroMaterial", scriptlib::duel_select_synchro_material },
	{ "CheckSynchroMaterial", scriptlib::duel_check_synchro_material },
	{ "SelectTunerMaterial", scriptlib::duel_select_tuner_material },
	{ "CheckTunerMaterial", scriptlib::duel_check_tuner_material },
	{ "GetRitualMaterial", scriptlib::duel_get_ritual_material },
	{ "GetRitualMaterialEx", scriptlib::duel_get_ritual_material_ex },
	{ "ReleaseRitualMaterial", scriptlib::duel_release_ritual_material },
	{ "GetFusionMaterial", scriptlib::duel_get_fusion_material },
	{ "IsSummonCancelable", scriptlib::duel_is_summon_cancelable },
	{ "SetSelectedCard", scriptlib::duel_set_must_select_cards },
	{ "GrabSelectedCard", scriptlib::duel_grab_must_select_cards },
	{ "SetTargetCard", scriptlib::duel_set_target_card },
	{ "ClearTargetCard", scriptlib::duel_clear_target_card },
	{ "SetTargetPlayer", scriptlib::duel_set_target_player },
	{ "SetTargetParam", scriptlib::duel_set_target_param },
	{ "SetOperationInfo", scriptlib::duel_set_operation_info },
	{ "GetOperationInfo", scriptlib::duel_get_operation_info },
	{ "GetOperationCount", scriptlib::duel_get_operation_count },
	{ "ClearOperationInfo", scriptlib::duel_clear_operation_info },
	{ "CheckXyzMaterial", scriptlib::duel_check_xyz_material },
	{ "SelectXyzMaterial", scriptlib::duel_select_xyz_material },
	{ "Overlay", scriptlib::duel_overlay },
	{ "GetOverlayGroup", scriptlib::duel_get_overlay_group },
	{ "GetOverlayCount", scriptlib::duel_get_overlay_count },
	{ "CheckRemoveOverlayCard", scriptlib::duel_check_remove_overlay_card },
	{ "RemoveOverlayCard", scriptlib::duel_remove_overlay_card },
	{ "Hint", scriptlib::duel_hint },
	{ "GetLastSelectHint", scriptlib::duel_get_last_select_hint },
	{ "HintSelection", scriptlib::duel_hint_selection },
	{ "SelectEffectYesNo", scriptlib::duel_select_effect_yesno },
	{ "SelectYesNo", scriptlib::duel_select_yesno },
	{ "SelectOption", scriptlib::duel_select_option },
	{ "SelectSequence", scriptlib::duel_select_sequence },
	{ "SelectPosition", scriptlib::duel_select_position },
	{ "SelectField", scriptlib::duel_select_field },
	{ "SelectDisableField", scriptlib::duel_select_disable_field },
	{ "AnnounceRace", scriptlib::duel_announce_race },
	{ "AnnounceAttribute", scriptlib::duel_announce_attribute },
	{ "AnnounceLevel", scriptlib::duel_announce_level },
	{ "AnnounceCard", scriptlib::duel_announce_card },
	{ "AnnounceType", scriptlib::duel_announce_type },
	{ "AnnounceNumber", scriptlib::duel_announce_number },
	{ "AnnounceCoin", scriptlib::duel_announce_coin },
	{ "TossCoin", scriptlib::duel_toss_coin },
	{ "TossDice", scriptlib::duel_toss_dice },
	{ "RockPaperScissors", scriptlib::duel_rock_paper_scissors },
	{ "GetCoinResult", scriptlib::duel_get_coin_result },
	{ "GetDiceResult", scriptlib::duel_get_dice_result },
	{ "SetCoinResult", scriptlib::duel_set_coin_result },
	{ "SetDiceResult", scriptlib::duel_set_dice_result },
	{ "IsPlayerAffectedByEffect", scriptlib::duel_is_player_affected_by_effect },
	{ "IsPlayerCanDraw", scriptlib::duel_is_player_can_draw },
	{ "IsPlayerCanDiscardDeck", scriptlib::duel_is_player_can_discard_deck },
	{ "IsPlayerCanDiscardDeckAsCost", scriptlib::duel_is_player_can_discard_deck_as_cost },
	{ "IsPlayerCanSummon", scriptlib::duel_is_player_can_summon },
	{ "IsPlayerCanMSet", scriptlib::duel_is_player_can_mset },
	{ "IsPlayerCanSSet", scriptlib::duel_is_player_can_sset },
	{ "IsPlayerCanSpecialSummon", scriptlib::duel_is_player_can_spsummon },
	{ "IsPlayerCanFlipSummon", scriptlib::duel_is_player_can_flipsummon },
	{ "IsPlayerCanSpecialSummonMonster", scriptlib::duel_is_player_can_spsummon_monster },
	{ "IsPlayerCanSpecialSummonCount", scriptlib::duel_is_player_can_spsummon_count },
	{ "IsPlayerCanRelease", scriptlib::duel_is_player_can_release },
	{ "IsPlayerCanRemove", scriptlib::duel_is_player_can_remove },
	{ "IsPlayerCanSendtoHand", scriptlib::duel_is_player_can_send_to_hand },
	{ "IsPlayerCanSendtoGrave", scriptlib::duel_is_player_can_send_to_grave },
	{ "IsPlayerCanSendtoDeck", scriptlib::duel_is_player_can_send_to_deck },
	{ "IsPlayerCanAdditionalSummon", scriptlib::duel_is_player_can_additional_summon },
	{ "IsChainSolving", scriptlib::duel_is_chain_solving },
	{ "IsChainNegatable", scriptlib::duel_is_chain_negatable },
	{ "IsChainDisablable", scriptlib::duel_is_chain_disablable },
	{ "IsChainDisabled", scriptlib::duel_is_chain_disabled },
	{ "CheckChainTarget", scriptlib::duel_check_chain_target },
	{ "CheckChainUniqueness", scriptlib::duel_check_chain_uniqueness },
	{ "GetActivityCount", scriptlib::duel_get_activity_count },
	{ "CheckPhaseActivity", scriptlib::duel_check_phase_activity },
	{ "AddCustomActivityCounter", scriptlib::duel_add_custom_activity_counter },
	{ "GetCustomActivityCount", scriptlib::duel_get_custom_activity_count },
	{ "GetBattledCount", scriptlib::duel_get_battled_count },
	{ "IsAbleToEnterBP", scriptlib::duel_is_able_to_enter_bp },
	{ "SwapDeckAndGrave", scriptlib::duel_swap_deck_and_grave },
	{ "MajesticCopy", scriptlib::duel_majestic_copy },
	{ nullptr, nullptr }
};
void scriptlib::open_duellib(lua_State *L) {
	luaL_newlib(L, duellib);
	lua_setglobal(L, "Duel");
}
