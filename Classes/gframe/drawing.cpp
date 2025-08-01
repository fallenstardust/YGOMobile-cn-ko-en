#include "game.h"
#include "client_card.h"
#include "materials.h"
#include "image_manager.h"
#include "deck_manager.h"
#include "duelclient.h"

namespace ygo {

inline void SetS3DVertex(irr::video::S3DVertex* v, irr::f32 x1, irr::f32 y1, irr::f32 x2, irr::f32 y2, irr::f32 z, irr::f32 nz,irr::f32 tu1, irr::f32 tv1, irr::f32 tu2, irr::f32 tv2) {
	v[0] = irr::video::S3DVertex(x1, y1, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu1, tv1);
	v[1] = irr::video::S3DVertex(x2, y1, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu2, tv1);
	v[2] = irr::video::S3DVertex(x1, y2, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu1, tv2);
	v[3] = irr::video::S3DVertex(x2, y2, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu2, tv2);
}
void Game::SetCardS3DVertex() {
    irr::f32 defalutScale = (mainGame->xScale - mainGame->yScale) / 9.5f;
    ALOGD("cc drawing defalutScale = %f",defalutScale);
    SetS3DVertex(matManager.vCardFront, -0.35f + defalutScale, -0.5f, 0.35f - defalutScale, 0.5f, 0, 1, 0, 0, 1, 1);
    SetS3DVertex(matManager.vCardOutline, -0.375f + defalutScale, -0.54f, 0.37f - defalutScale, 0.54f, 0, 1, 0, 0, 1, 1);
    SetS3DVertex(matManager.vCardOutliner, 0.37f - defalutScale, -0.54f, -0.375f + defalutScale, 0.54f, 0, 1, 0, 0, 1, 1);
    SetS3DVertex(matManager.vCardBack, 0.35f - defalutScale, -0.5f, -0.35f + defalutScale, 0.5f, 0, -1, 0, 0, 1, 1);
}
void Game::DrawSelectionLine(irr::video::S3DVertex* vec, bool strip, int width, float* cv) {
		glLineWidth(width+2);
		driver->setMaterial(matManager.mOutLine);
		if(strip) {
			if(linePatternD3D < 15) {
				driver->draw3DLine(vec[0].Pos, vec[0].Pos + (vec[1].Pos - vec[0].Pos) * (linePatternD3D + 1) / 15.0);
				driver->draw3DLine(vec[1].Pos, vec[1].Pos + (vec[3].Pos - vec[1].Pos) * (linePatternD3D + 1) / 15.0);
				driver->draw3DLine(vec[3].Pos, vec[3].Pos + (vec[2].Pos - vec[3].Pos) * (linePatternD3D + 1) / 15.0);
				driver->draw3DLine(vec[2].Pos, vec[2].Pos + (vec[0].Pos - vec[2].Pos) * (linePatternD3D + 1) / 15.0);
			} else {
				driver->draw3DLine(vec[0].Pos + (vec[1].Pos - vec[0].Pos) * (linePatternD3D - 14) / 15.0, vec[1].Pos);
				driver->draw3DLine(vec[1].Pos + (vec[3].Pos - vec[1].Pos) * (linePatternD3D - 14) / 15.0, vec[3].Pos);
				driver->draw3DLine(vec[3].Pos + (vec[2].Pos - vec[3].Pos) * (linePatternD3D - 14) / 15.0, vec[2].Pos);
				driver->draw3DLine(vec[2].Pos + (vec[0].Pos - vec[2].Pos) * (linePatternD3D - 14) / 15.0, vec[0].Pos);
			}
		} else {
			driver->draw3DLine(vec[0].Pos, vec[1].Pos);
			driver->draw3DLine(vec[1].Pos, vec[3].Pos);
			driver->draw3DLine(vec[3].Pos, vec[2].Pos);
			driver->draw3DLine(vec[2].Pos, vec[0].Pos);
		}

}
void Game::DrawSelectionLine(irr::gui::IGUIElement* element, int width, irr::video::SColor color) {
	irr::core::recti pos = element->getAbsolutePosition();
	float x1 = pos.UpperLeftCorner.X;
	float x2 = pos.LowerRightCorner.X;
	float y1 = pos.UpperLeftCorner.Y;
	float y2 = pos.LowerRightCorner.Y;
	float w = pos.getWidth();
	float h = pos.getHeight();
	if(linePatternD3D < 15) {
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y1 - 1 - width, x1 + (w * (linePatternD3D + 1) / 15.0) + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, irr::core::recti(x2 - (w * (linePatternD3D + 1) / 15.0) - 1 - width, y2 + 1, x2 + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y1 - 1 - width, x1 - 1, y2 - (h * (linePatternD3D + 1) / 15.0) + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x2 + 1, y1 + (h * (linePatternD3D + 1) / 15.0) - 1 - width, x2 + 1 + width, y2 + 1 + width));
	} else {
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width + (w * (linePatternD3D - 14) / 15.0), y1 - 1 - width, x2 + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y2 + 1, x2 - (w * (linePatternD3D - 14) / 15.0) + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y2 - (h * (linePatternD3D - 14) / 15.0) - 1 - width, x1 - 1, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x2 + 1, y1 - 1 - width, x2 + 1 + width, y1 + (h * (linePatternD3D - 14) / 15.0) + 1 + width));
	}
}
void Game::DrawBackGround() {
	static int selFieldAlpha = 255;
	static int selFieldDAlpha = -10;
//	matrix4 im = irr::core::IdentityMatrix;
//	im.setTranslation(irr::core::vector3df(0, 0, -0.01f));
//	driver->setTransform(irr::video::ETS_WORLD, im);
	//dark shade
//	matManager.mSelField.AmbientColor = 0xff000000;
//	matManager.mSelField.DiffuseColor = 0xa0000000;
//	driver->setMaterial(matManager.mSelField);
//	for(int i = 0; i < 120; i += 4)
//		driver->drawVertexPrimitiveList(&matManager.vFields[i], 4, matManager.iRectangle, 2);
//	driver->setTransform(irr::video::ETS_WORLD, irr::core::IdentityMatrix);
//	driver->setMaterial(matManager.mBackLine);
//	driver->drawVertexPrimitiveList(matManager.vBackLine, 76, matManager.iBackLine, 58, irr::video::EVT_STANDARD, irr::scene::EPT_LINES);
	//draw field
	//draw field spell card
	driver->setTransform(irr::video::ETS_WORLD, irr::core::IdentityMatrix);
	bool drawField = false;
	int rule = (dInfo.duel_rule >= 4) ? 1 : 0;
	if(gameConf.draw_field_spell) {
		int fieldcode1 = -1;
		int fieldcode2 = -1;
		if(dField.szone[0][5] && dField.szone[0][5]->position & POS_FACEUP)
			fieldcode1 = dField.szone[0][5]->code;
		if(dField.szone[1][5] && dField.szone[1][5]->position & POS_FACEUP)
			fieldcode2 = dField.szone[1][5]->code;
		int fieldcode = (fieldcode1 > 0) ? fieldcode1 : fieldcode2;
		if(fieldcode1 > 0 && fieldcode2 > 0 && fieldcode1 != fieldcode2) {
			auto texture = imageManager.GetTextureField(fieldcode1);
			if(texture) {
				drawField = true;
				matManager.mTexture.setTexture(0, texture);
				driver->setMaterial(matManager.mTexture);
				driver->drawVertexPrimitiveList(matManager.vFieldSpell1, 4, matManager.iRectangle, 2);
			}
			texture = imageManager.GetTextureField(fieldcode2);
			if(texture) {
				drawField = true;
				matManager.mTexture.setTexture(0, texture);
				driver->setMaterial(matManager.mTexture);
				driver->drawVertexPrimitiveList(matManager.vFieldSpell2, 4, matManager.iRectangle, 2);
			}
		} else if(fieldcode > 0) {
			auto texture = imageManager.GetTextureField(fieldcode);
			if(texture) {
				drawField = true;
				matManager.mTexture.setTexture(0, texture);
				driver->setMaterial(matManager.mTexture);
				driver->drawVertexPrimitiveList(matManager.vFieldSpell, 4, matManager.iRectangle, 2);
			}
		}
	}
	matManager.mTexture.setTexture(0, drawField ? imageManager.tFieldTransparent[rule] : imageManager.tField[rule]);
	driver->setMaterial(matManager.mTexture);
	driver->drawVertexPrimitiveList(matManager.vField, 4, matManager.iRectangle, 2);
	driver->setMaterial(matManager.mBackLine);
	//select field
	if(dInfo.curMsg == MSG_SELECT_PLACE || dInfo.curMsg == MSG_SELECT_DISFIELD || dInfo.curMsg == MSG_HINT) {
		float cv[4] = {0.0f, 0.0f, 1.0f, 1.0f};
		unsigned int filter = 0x1;
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldMzone[0][i], !(dField.selected_field & filter), 2, cv);
		}
		filter = 0x100;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldSzone[0][i][rule], !(dField.selected_field & filter), 2, cv);
		}
		filter = 0x10000;
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldMzone[1][i], !(dField.selected_field & filter), 2, cv);
		}
		filter = 0x1000000;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldSzone[1][i][rule], !(dField.selected_field & filter), 2, cv);
		}
	}
	//draw total attack
	if (mainGame->dInfo.total_attack[0] > 0) {
	    matManager.mTexture.setTexture(0, imageManager.tTotalAtk);
		driver->setMaterial(matManager.mTexture);
		if (dInfo.duel_rule >= 4) {
		    driver->drawVertexPrimitiveList(matManager.vTotalAtkme, 4, matManager.iRectangle, 2);
			DrawShadowText(numFont, dInfo.str_total_attack[0], Resize(430, 346, 445, 366), Resize(0, 1, 2, 0), dInfo.total_attack_color[0], 0xff000000, true, false, 0);
	    } else {
			driver->drawVertexPrimitiveList(matManager.vTotalAtkmeT, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[0], Resize(590, 326, 610, 346), Resize(0, 1, 2, 0), dInfo.total_attack_color[0], 0xff000000, true, false);
	    }
	}
	if (mainGame->dInfo.total_attack[1] > 0) {
		matManager.mTexture.setTexture(0, imageManager.tTotalAtk);
		driver->setMaterial(matManager.mTexture);
		if (dInfo.duel_rule >= 4) {
		    driver->drawVertexPrimitiveList(matManager.vTotalAtkop, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[1], Resize(885, 271, 905, 291), Resize(0, 1, 2, 0), dInfo.total_attack_color[1], 0xff000000, true, false);
	    } else {
			driver->drawVertexPrimitiveList(matManager.vTotalAtkopT, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[1], Resize(740, 295, 760, 315), Resize(0, 1, 2, 0), dInfo.total_attack_color[1], 0xff000000, true, false);

	    }
	}

	//disabled field
	{
		/*float cv[4] = {0.0f, 0.0f, 1.0f, 1.0f};*/
		unsigned int filter = 0x1;
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.disabled_field & filter) {
				driver->draw3DLine(matManager.vFieldMzone[0][i][0].Pos, matManager.vFieldMzone[0][i][3].Pos, 0xffffffff);
				driver->draw3DLine(matManager.vFieldMzone[0][i][1].Pos, matManager.vFieldMzone[0][i][2].Pos, 0xffffffff);
			}
		}
		filter = 0x100;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.disabled_field & filter) {
				driver->draw3DLine(matManager.vFieldSzone[0][i][rule][0].Pos, matManager.vFieldSzone[0][i][rule][3].Pos, 0xffffffff);
				driver->draw3DLine(matManager.vFieldSzone[0][i][rule][1].Pos, matManager.vFieldSzone[0][i][rule][2].Pos, 0xffffffff);
			}
		}
		filter = 0x10000;
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.disabled_field & filter) {
				driver->draw3DLine(matManager.vFieldMzone[1][i][0].Pos, matManager.vFieldMzone[1][i][3].Pos, 0xffffffff);
				driver->draw3DLine(matManager.vFieldMzone[1][i][1].Pos, matManager.vFieldMzone[1][i][2].Pos, 0xffffffff);
			}
		}
		filter = 0x1000000;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.disabled_field & filter) {
				driver->draw3DLine(matManager.vFieldSzone[1][i][rule][0].Pos, matManager.vFieldSzone[1][i][rule][3].Pos, 0xffffffff);
				driver->draw3DLine(matManager.vFieldSzone[1][i][rule][1].Pos, matManager.vFieldSzone[1][i][rule][2].Pos, 0xffffffff);
			}
		}
	}
	//current sel
	if (dField.hovered_location != 0 && dField.hovered_location != 2 && dField.hovered_location != POSITION_HINT
		&& !(dInfo.duel_rule < 4 && dField.hovered_location == LOCATION_MZONE && dField.hovered_sequence > 4)
		&& !(dInfo.duel_rule >= 4 && dField.hovered_location == LOCATION_SZONE && dField.hovered_sequence > 5)) {
#ifdef _IRR_ANDROID_PLATFORM_
		if (dField.hovered_location == LOCATION_MZONE) {
			ClientCard* pcard = mainGame->dField.mzone[dField.hovered_controler][dField.hovered_sequence];
			if(pcard && pcard->type & TYPE_LINK) {
				DrawLinkedZones(pcard);
			}
		}
		DrawSelField(dField.hovered_controler, dField.hovered_location, dField.hovered_sequence, imageManager.tSelField, false);
#endif
	}
}
void Game::DrawSelField(int player, int loc, size_t seq, irr::video::ITexture* texture, bool reverse, bool spin) {
	static irr::core::vector3df act_rot(0, 0, 0);
	irr::core::vector3df t;
	irr::core::matrix4 im;
	dField.GetChainLocation(player, loc, seq, &t);
	t.Z = spin ? 0.002f : 0.001f;
	im.setTranslation(t);
	if (spin) {
		act_rot.Z += 0.02f;
		im.setRotationRadians(act_rot);
	}
	if (reverse) {
		im.setRotationRadians(irr::core::vector3df(0, 0, 3.1415926f));
	}
	driver->setTransform(irr::video::ETS_WORLD, im);
	matManager.mTexture.setTexture(0, texture);
	driver->setMaterial(matManager.mTexture);
	driver->drawVertexPrimitiveList(matManager.vSelField, 4, matManager.iRectangle, 2);
}

void Game::DrawLinkedZones(ClientCard* pcard, ClientCard* fcard) {
	int mark = pcard->link_marker;
	int player = pcard->controler;
	int seq = pcard->sequence;
	bool reverse = player == 1;
	ClientCard* pcard2;
	if (seq < 5) {
		if (mark & LINK_MARKER_LEFT && seq > 0) {
			DrawSelField(player, LOCATION_MZONE, seq - 1, imageManager.tSelFieldLinkArrows[4], reverse);
			//pcard2 = dField.mzone[player][seq - 1];
			//if (pcard2 && pcard2 != fcard && pcard2->link_marker & LINK_MARKER_RIGHT)
			//	DrawLinkedZones(pcard2, pcard);
		}
		if (mark & LINK_MARKER_RIGHT && seq < 4) {
			DrawSelField(player, LOCATION_MZONE, seq + 1, imageManager.tSelFieldLinkArrows[6], reverse);
			//pcard2 = dField.mzone[player][seq + 1];
			//if (pcard2 && pcard2 != fcard && pcard2->link_marker & LINK_MARKER_LEFT)
			//	DrawLinkedZones(pcard2, pcard);
		}
		if (dInfo.duel_rule >= 4) {
			if (mark & LINK_MARKER_TOP_RIGHT && seq == 0)
				DrawSelField(player, LOCATION_MZONE, 5, imageManager.tSelFieldLinkArrows[9], reverse);
			if (mark & LINK_MARKER_TOP && seq == 1)
				DrawSelField(player, LOCATION_MZONE, 5, imageManager.tSelFieldLinkArrows[8], reverse);
			if (mark & LINK_MARKER_TOP_LEFT && seq == 2)
				DrawSelField(player, LOCATION_MZONE, 5, imageManager.tSelFieldLinkArrows[7], reverse);
			if (mark & LINK_MARKER_TOP_RIGHT && seq == 2)
				DrawSelField(player, LOCATION_MZONE, 6, imageManager.tSelFieldLinkArrows[9], reverse);
			if (mark & LINK_MARKER_TOP && seq == 3)
				DrawSelField(player, LOCATION_MZONE, 6, imageManager.tSelFieldLinkArrows[8], reverse);
			if (mark & LINK_MARKER_TOP_LEFT && seq == 4)
				DrawSelField(player, LOCATION_MZONE, 6, imageManager.tSelFieldLinkArrows[7], reverse);
		}
	} else {
		int swap = (dField.hovered_sequence == 5) ? 0 : 2;
		if (mark & LINK_MARKER_BOTTOM_LEFT)
			DrawSelField(player, LOCATION_MZONE, 0 + swap, imageManager.tSelFieldLinkArrows[1], reverse);
		if (mark & LINK_MARKER_BOTTOM)
			DrawSelField(player, LOCATION_MZONE, 1 + swap, imageManager.tSelFieldLinkArrows[2], reverse);
		if (mark & LINK_MARKER_BOTTOM_RIGHT)
			DrawSelField(player, LOCATION_MZONE, 2 + swap, imageManager.tSelFieldLinkArrows[3], reverse);
		if (mark & LINK_MARKER_TOP_LEFT)
			DrawSelField(1 - player, LOCATION_MZONE, 4 - swap, imageManager.tSelFieldLinkArrows[7], reverse);
		if (mark & LINK_MARKER_TOP)
			DrawSelField(1 - player, LOCATION_MZONE, 3 - swap, imageManager.tSelFieldLinkArrows[8], reverse);
		if (mark & LINK_MARKER_TOP_RIGHT)
			DrawSelField(1 - player, LOCATION_MZONE, 2 - swap, imageManager.tSelFieldLinkArrows[9], reverse);
	}
}

void Game::DrawCards() {
    for (auto cit = dField.overlay_cards.begin(); cit != dField.overlay_cards.end(); ++cit) {
        auto pcard = (*cit);
        auto olcard = pcard->overlayTarget;
        if (pcard->aniFrame) {
            DrawCard(pcard);
        }
        else if (olcard && olcard->location == LOCATION_MZONE) {
            if (pcard->sequence < MAX_LAYER_COUNT) {
                DrawCard(pcard);
            }
        }
        else {
            DrawCard(pcard);
        }
    }
	for(int p = 0; p < 2; ++p) {
		for(auto it = dField.mzone[p].begin(); it != dField.mzone[p].end(); ++it)
			if(*it)
				DrawCard(*it);
		for(auto it = dField.szone[p].begin(); it != dField.szone[p].end(); ++it)
			if(*it)
				DrawCard(*it);
		for(auto it = dField.deck[p].begin(); it != dField.deck[p].end(); ++it)
			DrawCard(*it);
		for(auto it = dField.hand[p].begin(); it != dField.hand[p].end(); ++it)
			DrawCard(*it);
		for(auto it = dField.grave[p].begin(); it != dField.grave[p].end(); ++it)
			DrawCard(*it);
		for(auto it = dField.remove[p].begin(); it != dField.remove[p].end(); ++it)
			DrawCard(*it);
		for(auto it = dField.extra[p].begin(); it != dField.extra[p].end(); ++it)
			DrawCard(*it);
	}
}
void Game::DrawCard(ClientCard* pcard) {
	if(pcard->aniFrame) {
		if(pcard->is_moving) {
			pcard->curPos += pcard->dPos;
			pcard->curRot += pcard->dRot;
			pcard->mTransform.setTranslation(pcard->curPos);
			pcard->mTransform.setRotationRadians(pcard->curRot);
		}
		if(pcard->is_fading)
			pcard->curAlpha += pcard->dAlpha;
		pcard->aniFrame--;
		if(pcard->aniFrame == 0) {
			pcard->is_moving = false;
			pcard->is_fading = false;
		}
	}
	matManager.mCard.AmbientColor = 0xffffffff;
	matManager.mCard.DiffuseColor = (pcard->curAlpha << 24) | 0xffffff;
	driver->setTransform(irr::video::ETS_WORLD, pcard->mTransform);
	auto m22 = pcard->mTransform(2, 2);
	if(m22 > -0.99 || pcard->is_moving) {
		matManager.mCard.setTexture(0, imageManager.GetTexture(pcard->code));
		driver->setMaterial(matManager.mCard);
		driver->drawVertexPrimitiveList(matManager.vCardFront, 4, matManager.iRectangle, 2);
	}
	if(m22 < 0.99 || pcard->is_moving) {
		matManager.mCard.setTexture(0, imageManager.tCover[pcard->controler]);
		driver->setMaterial(matManager.mCard);
		driver->drawVertexPrimitiveList(matManager.vCardBack, 4, matManager.iRectangle, 2);
	}
	if(pcard->is_moving)
		return;
	if(pcard->is_selectable && (pcard->location & 0xe)) {
		float cv[4] = {1.0f, 1.0f, 0.0f, 1.0f};
		if((pcard->location == LOCATION_HAND && pcard->code) || ((pcard->location & 0xc) && (pcard->position & POS_FACEUP)))
			DrawSelectionLine(matManager.vCardOutline, !pcard->is_selected, 2, cv);
		else
			DrawSelectionLine(matManager.vCardOutliner, !pcard->is_selected, 2, cv);
	}
	if(pcard->is_highlighting) {
		float cv[4] = {0.0f, 1.0f, 1.0f, 1.0f};
		if((pcard->location == LOCATION_HAND && pcard->code) || ((pcard->location & 0xc) && (pcard->position & POS_FACEUP)))
			DrawSelectionLine(matManager.vCardOutline, true, 2, cv);
		else
			DrawSelectionLine(matManager.vCardOutliner, true, 2, cv);
	}
	irr::core::matrix4 im;
	im.setTranslation(pcard->curPos);
	driver->setTransform(irr::video::ETS_WORLD, im);
	if(pcard->is_showequip) {
		matManager.mTexture.setTexture(0, imageManager.tEquip);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	} else if(pcard->is_showtarget) {
		matManager.mTexture.setTexture(0, imageManager.tTarget);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	} else if(pcard->is_showchaintarget) {
		matManager.mTexture.setTexture(0, imageManager.tChainTarget);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	} else if((pcard->status & (STATUS_DISABLED | STATUS_FORBIDDEN))
		&& (pcard->location & LOCATION_ONFIELD) && (pcard->position & POS_FACEUP)) {
		matManager.mTexture.setTexture(0, imageManager.tNegated);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);
	}
	if(pcard->cmdFlag & COMMAND_ATTACK) {
		matManager.mTexture.setTexture(0, imageManager.tAttack);
		driver->setMaterial(matManager.mTexture);
		irr::core::matrix4 atk;
		atk.setTranslation(pcard->curPos + irr::core::vector3df(0, (pcard->controler == 0 ? -1 : 1) * (atkdy / 4.0f + 0.35f), 0.05f));
		atk.setRotationRadians(irr::core::vector3df(0, 0, pcard->controler == 0 ? 0 : 3.1415926f));
		driver->setTransform(irr::video::ETS_WORLD, atk);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	}
	if (isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence > 5)) {
		int scale = pcard->sequence == 6 ? pcard->lscale : pcard->rscale;
		matManager.mTexture.setTexture(0, pcard->sequence == 6 ? imageManager.tLScale[scale] : imageManager.tRScale[scale]);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
	}
    if(dInfo.duel_rule >= 4) {
	  if (isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence == 0)) {
		int scale = pcard->lscale;
		matManager.mTexture.setTexture(0, imageManager.tLScale[scale]);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
	  }//pendulum LEFT scale image
	  if (isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence == 4)) {
		int scale2 = pcard->rscale;
		matManager.mTexture.setTexture(0, imageManager.tRScale[scale2]);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
	  }//pendulum RIGHT scale image
	}
}
template<typename T>
void Game::DrawShadowText(irr::gui::CGUITTFont* font, const T& text, const irr::core::rect<irr::s32>& position, const irr::core::rect<irr::s32>& padding,
			irr::video::SColor color, irr::video::SColor shadowcolor, bool hcenter, bool vcenter, const irr::core::rect<irr::s32>* clip) {
	irr::core::rect<irr::s32> shadowposition = irr::core::recti(position.UpperLeftCorner.X - padding.UpperLeftCorner.X, position.UpperLeftCorner.Y - padding.UpperLeftCorner.Y, 
										   position.LowerRightCorner.X - padding.LowerRightCorner.X, position.LowerRightCorner.Y - padding.LowerRightCorner.Y);
	font->drawUstring(text, shadowposition, shadowcolor, hcenter, vcenter, clip);
	font->drawUstring(text, position, color, hcenter, vcenter, clip);
}
void Game::DrawMisc() {
	static irr::core::vector3df act_rot(0, 0, 0);
	int rule = (dInfo.duel_rule >= 4) ? 1 : 0;
	irr::core::matrix4 im, ic, it, ig;
	act_rot.Z += 0.02f;
	im.setRotationRadians(act_rot);
	matManager.mTexture.setTexture(0, imageManager.tAct);
	driver->setMaterial(matManager.mTexture);
	for(int player = 0; player < 2; ++player) {
		if(dField.deck_act[player]) {
			im.setTranslation(irr::core::vector3df((matManager.vFieldDeck[player][0].Pos.X + matManager.vFieldDeck[player][1].Pos.X) / 2,
				(matManager.vFieldDeck[player][0].Pos.Y + matManager.vFieldDeck[player][2].Pos.Y) / 2, dField.deck[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
		if(dField.grave_act[player]) {
			im.setTranslation(irr::core::vector3df((matManager.vFieldGrave[player][rule][0].Pos.X + matManager.vFieldGrave[player][rule][1].Pos.X) / 2,
				(matManager.vFieldGrave[player][rule][0].Pos.Y + matManager.vFieldGrave[player][rule][2].Pos.Y) / 2, dField.grave[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
		if(dField.remove_act[player]) {
			im.setTranslation(irr::core::vector3df((matManager.vFieldRemove[player][rule][0].Pos.X + matManager.vFieldRemove[player][rule][1].Pos.X) / 2,
				(matManager.vFieldRemove[player][rule][0].Pos.Y + matManager.vFieldRemove[player][rule][2].Pos.Y) / 2, dField.remove[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
		if(dField.extra_act[player]) {
			im.setTranslation(irr::core::vector3df((matManager.vFieldExtra[player][0].Pos.X + matManager.vFieldExtra[player][1].Pos.X) / 2,
				(matManager.vFieldExtra[player][0].Pos.Y + matManager.vFieldExtra[player][2].Pos.Y) / 2, dField.extra[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
		if(dField.pzone_act[player]) {
			int seq = dInfo.duel_rule >= 4 ? 0 : 6;
			im.setTranslation(irr::core::vector3df((matManager.vFieldSzone[player][seq][rule][0].Pos.X + matManager.vFieldSzone[player][seq][rule][1].Pos.X) / 2,
				(matManager.vFieldSzone[player][seq][rule][0].Pos.Y + matManager.vFieldSzone[player][seq][rule][2].Pos.Y) / 2, 0.03f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
	}
	if(dField.conti_act) {
		irr::core::vector3df pos = irr::core::vector3df((matManager.vFieldContiAct[0].X + matManager.vFieldContiAct[1].X) / 2,
			(matManager.vFieldContiAct[0].Y + matManager.vFieldContiAct[2].Y) / 2, 0);
		im.setRotationRadians(irr::core::vector3df(0, 0, 0));
		for(auto cit = dField.conti_cards.begin(); cit != dField.conti_cards.end(); ++cit) {
			im.setTranslation(pos);
			driver->setTransform(irr::video::ETS_WORLD, im);
			matManager.mCard.setTexture(0, imageManager.GetTexture((*cit)->code));
			driver->setMaterial(matManager.mCard);
			driver->drawVertexPrimitiveList(matManager.vCardFront, 4, matManager.iRectangle, 2);
			pos.Z += 0.03f;
		}
		im.setTranslation(pos);
		im.setRotationRadians(act_rot);
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.chains.size() > 1 || mainGame->gameConf.draw_single_chain) {
		for(size_t i = 0; i < dField.chains.size(); ++i) {
			if(dField.chains[i].solved)
				break;
			matManager.mTRTexture.setTexture(0, imageManager.tChain);
			matManager.mTRTexture.AmbientColor = 0xffffff00;
			ic.setRotationRadians(act_rot);
			ic.setTranslation(dField.chains[i].chain_pos);
			driver->setMaterial(matManager.mTRTexture);
			driver->setTransform(irr::video::ETS_WORLD, ic);
			driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
			it.setScale(0.6f);
			it.setTranslation(dField.chains[i].chain_pos);
			matManager.mTRTexture.setTexture(0, imageManager.tNumber);
			matManager.vChainNum[0].TCoords = irr::core::vector2df(0.19375f * (i % 5), 0.2421875f * (i / 5));
			matManager.vChainNum[1].TCoords = irr::core::vector2df(0.19375f * (i % 5 + 1), 0.2421875f * (i / 5));
			matManager.vChainNum[2].TCoords = irr::core::vector2df(0.19375f * (i % 5), 0.2421875f * (i / 5 + 1));
			matManager.vChainNum[3].TCoords = irr::core::vector2df(0.19375f * (i % 5 + 1), 0.2421875f * (i / 5 + 1));
			driver->setMaterial(matManager.mTRTexture);
			driver->setTransform(irr::video::ETS_WORLD, it);
			driver->drawVertexPrimitiveList(matManager.vChainNum, 4, matManager.iRectangle, 2);
		}
	}
	if(dField.cant_check_grave) {
		matManager.mTexture.setTexture(0, imageManager.tNegated);
		driver->setMaterial(matManager.mTexture);
		ig.setTranslation(irr::core::vector3df((matManager.vFieldGrave[0][rule][0].Pos.X + matManager.vFieldGrave[0][rule][1].Pos.X) / 2,
			(matManager.vFieldGrave[0][rule][0].Pos.Y + matManager.vFieldGrave[0][rule][2].Pos.Y) / 2, dField.grave[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, ig);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);
		ig.setTranslation(irr::core::vector3df((matManager.vFieldGrave[1][rule][0].Pos.X + matManager.vFieldGrave[1][rule][1].Pos.X) / 2,
			(matManager.vFieldGrave[1][rule][0].Pos.Y + matManager.vFieldGrave[1][rule][2].Pos.Y) / 2, dField.grave[1].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, ig);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);
	}
	//finish button
	if(btnCancelOrFinish->isVisible() && dField.select_ready)
		DrawSelectionLine(btnCancelOrFinish, 4, 0xff00ff00);
	if(btnLeaveGame->isVisible() && dField.tag_teammate_surrender)
		DrawSelectionLine(btnLeaveGame, 4, 0xff00ff00);
	//lp bar
	if(dInfo.start_lp) {
		auto maxLP = dInfo.isTag ? dInfo.start_lp / 2 : dInfo.start_lp;
		if(dInfo.lp[0] >= maxLP) {
			auto layerCount = dInfo.lp[0] / maxLP;
			auto partialLP = dInfo.lp[0] % maxLP;
			auto bgColorPos = (layerCount - 1) % 5;
			auto fgColorPos = layerCount % 5; 
		driver->draw2DImage(imageManager.tLPBar, Resize(390 + 235 * partialLP / maxLP, 12, 625, 74), irr::core::recti(0, bgColorPos * 60, 60, (bgColorPos + 1) * 60), 0, 0, true);
			if(partialLP > 0) {
				driver->draw2DImage(imageManager.tLPBar, Resize(390, 12, 390 + 235 * partialLP / maxLP, 74), irr::core::recti(0, fgColorPos * 60, 60, (fgColorPos + 1) * 60), 0, 0, true);
			}
		}
	else driver->draw2DImage(imageManager.tLPBar, Resize(390, 12, 390 + 235 * dInfo.lp[0] / maxLP, 74), irr::core::recti(0, 0, 60, 60), 0, 0, true);
		if(dInfo.lp[1] >= maxLP) {
			auto layerCount = dInfo.lp[1] / maxLP;
			auto partialLP = dInfo.lp[1] % maxLP;
			auto bgColorPos = (layerCount - 1) % 5;
			auto fgColorPos = layerCount % 5;
			driver->draw2DImage(imageManager.tLPBar, Resize(695, 12, 930 - 235 * partialLP / maxLP, 74), irr::core::recti(0, bgColorPos * 60, 60, (bgColorPos + 1) * 60), 0, 0, true);
			if(partialLP > 0) {
				driver->draw2DImage(imageManager.tLPBar, Resize(930 - 235 * partialLP / maxLP, 12, 930, 74), irr::core::recti(0, fgColorPos * 60, 60, (fgColorPos + 1) * 60), 0, 0, true);
			}
		}
		else driver->draw2DImage(imageManager.tLPBar, Resize(930 - 235 * dInfo.lp[1] / maxLP, 12, 930, 74), irr::core::recti(0, 0, 60, 60), 0, 0, true);
	}
	if(lpframe) {
		dInfo.lp[lpplayer] -= lpd;
		myswprintf(dInfo.strLP[lpplayer], L"%d", dInfo.lp[lpplayer]);
		lpccolor -= 0x19000000;
		lpframe--;
	}
	if(lpcstring.size()) {
		if(lpplayer == 0) {
            DrawShadowText(lpcFont, lpcstring, Resize(400, 470, 920, 520), Resize(0, 2, 2, 0), lpccolor, lpccolor | 0x00ffffff, true, false);
		} else {
            DrawShadowText(lpcFont, lpcstring, Resize(400, 160, 920, 210), Resize(0, 2, 2, 0), lpccolor, lpccolor | 0x00ffffff, true, false);
		}
	}
	//avatar image
	driver->draw2DImage(imageManager.tAvatar[0], Resize(335, 15, 390, 70), irr::core::recti(0, 0, 128, 128), 0, 0, true);
	driver->draw2DImage(imageManager.tAvatar[1], Resize(930, 15, 985, 70), irr::core::recti(0, 0, 128, 128), 0, 0, true);
	if((dInfo.turn % 2 && dInfo.isFirst) || (!(dInfo.turn % 2) && !dInfo.isFirst)) {
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(327, 8, 630, 78), irr::core::recti(0, 0, 305, 70), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(689, 8, 991, 78), irr::core::recti(0, 210, 305, 280), 0, 0, true);
	} else {
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(327, 8, 630, 78), irr::core::recti(0, 70, 305, 140), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(689, 8, 991, 78), irr::core::recti(0, 140, 305, 210), 0, 0, true);
	}
	//Time Display
	if(!dInfo.isReplay && dInfo.player_type < 7 && dInfo.time_limit) {
		if(imageManager.tClock) {
			driver->draw2DImage(imageManager.tClock, Resize(577, 50, 595, 68), irr::core::recti(0, 0, 34, 34), 0, 0, true);
			driver->draw2DImage(imageManager.tClock, Resize(695, 50, 713, 68), irr::core::recti(0, 0, 34, 34), 0, 0, true);
		}
		DrawShadowText(numFont, dInfo.str_time_left[0], Resize(595, 49, 625, 68), Resize(0, 1, 2, 0), dInfo.time_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_time_left[1], Resize(713, 49, 743, 68), Resize(0, 1, 2, 0), dInfo.time_color[1], 0xff000000, true, false);

		driver->draw2DImage(imageManager.tCover[0], Resize(537, 51, 550, 70), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[1], Resize(745, 51, 758, 70), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], Resize(550, 49, 575, 68), Resize(0, 1, 2, 0), dInfo.card_count_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_card_count[1], Resize(757, 49, 782, 68), Resize(0, 1, 2, 0), dInfo.card_count_color[1], 0xff000000, true, false);
	}
	else {
		driver->draw2DImage(imageManager.tCover[0], Resize(588, 48, 601, 68), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[1], Resize(697, 48, 710, 68), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], Resize(600, 51, 625, 70), Resize(0, 1, 2, 0), dInfo.card_count_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_card_count[1], Resize(710, 51, 735, 70), Resize(0, 1, 2, 0), dInfo.card_count_color[1], 0xff000000, true, false);
	}
    DrawShadowText(numFont,dInfo.strLP[0],Resize(305, 49, 614, 68),Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
    DrawShadowText(numFont,dInfo.strLP[1],Resize(711, 50, 1012, 69),Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	if(!gameConf.hide_player_name) {
		irr::core::recti p1size = Resize(400, 18, 629, 37);
		irr::core::recti p2size = Resize(920, 18, 986, 37);
		if(!dInfo.isTag || !dInfo.tag_player[0])
			guiFont->drawUstring(dInfo.hostname, p1size, 0xffffffff, false, false, 0);
		else
			guiFont->drawUstring(dInfo.hostname_tag, p1size, 0xffffffff, false, false, 0);
		if(!dInfo.isTag || !dInfo.tag_player[1]) {
			auto cld = guiFont->getDimension(dInfo.clientname);
			p2size.UpperLeftCorner.X -= cld.Width;
			guiFont->drawUstring(dInfo.clientname, p2size, 0xffffffff, false, false, 0);
		} else {
			auto cld = guiFont->getDimension(dInfo.clientname_tag);
			p2size.UpperLeftCorner.X -= cld.Width;
			guiFont->drawUstring(dInfo.clientname_tag, p2size, 0xffffffff, false, false, 0);
		}
	}
	driver->draw2DRectangle(Resize(632, 10, 688, 30), 0x00000000, 0x00000000, 0xffffffff, 0xffffffff);
	driver->draw2DRectangle(Resize(632, 30, 688, 50), 0xffffffff, 0xffffffff, 0x00000000, 0x00000000);
    DrawShadowText(lpcFont, dataManager.GetNumString(dInfo.turn), Resize(635, 5, 685, 40), Resize(0, 0, 2, 0),0x80000000, 0x8000ffff, true, false);
	ClientCard* pcard;
	for(int i = 0; i < 5; ++i) {
		pcard = dField.mzone[0][i];
		if(pcard && pcard->code != 0)
			DrawStatus(pcard, 493 + i * 85, 416, 473 + i * 80, 356);
	}
	pcard = dField.mzone[0][5];
	if(pcard && pcard->code != 0)
		DrawStatus(pcard, 589, 338, 563, 291);
	pcard = dField.mzone[0][6];
	if(pcard && pcard->code != 0)
		DrawStatus(pcard, 743, 338, 712, 291);
	for(int i = 0; i < 5; ++i) {
		pcard = dField.mzone[1][i];
		if(pcard && (pcard->position & POS_FACEUP))
			DrawStatus(pcard, 803 - i * 68, 235, 779 - i * 71, 272);
	}
	pcard = dField.mzone[1][5];
	if(pcard && (pcard->position & POS_FACEUP))
		DrawStatus(pcard, 739, 291, 710, 338);
	pcard = dField.mzone[1][6];
	if(pcard && (pcard->position & POS_FACEUP))
		DrawStatus(pcard, 593, 291, 555, 338);
	if(dInfo.duel_rule < 4) {
		pcard = dField.szone[0][6];
		if(pcard) {
        DrawShadowText(adFont, pcard->lscstring, Resize(426, 394, 438, 414), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[0][7];
		if(pcard) {
        DrawShadowText(adFont, pcard->lscstring, Resize(880, 394, 912, 414), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[1][6];
		if(pcard) {
        DrawShadowText(adFont, pcard->lscstring, Resize(839, 245, 871, 265), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[1][7];
		if(pcard) {
        DrawShadowText(adFont, pcard->lscstring, Resize(463, 245, 495, 265), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
	} else {
		pcard = dField.szone[0][0];
		if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
        DrawShadowText(adFont, pcard->lscstring, Resize(454, 430, 466, 450), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[0][4];
		if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
        DrawShadowText(adFont, pcard->lscstring, Resize(850, 430, 882, 450), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[1][0];
		if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
        DrawShadowText(adFont, pcard->lscstring, Resize(806, 222, 838, 242), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
		pcard = dField.szone[1][4];
		if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
        DrawShadowText(adFont, pcard->lscstring, Resize(498, 222, 530, 242), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
		}
	}
	if(dField.extra[0].size()) {
		int offset = (dField.extra[0].size() >= 10) ? 0 : mainGame->numFont->getDimension(dataManager.GetNumString(1)).Width;
        DrawShadowText(numFont, dataManager.GetNumString(dField.extra[0].size()), Resize(320 + offset, 562, 371, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
        DrawShadowText(numFont, dataManager.GetNumString(dField.extra_p_count[0], true), Resize(340, 562, 391, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
	if(dField.deck[0].size()) {
        DrawShadowText(numFont, dataManager.GetNumString(dField.deck[0].size()), Resize(907, 562, 1021, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
	if (rule == 0) {
		if (dField.grave[0].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.grave[0].size()), Resize(837, 375, 984, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if (dField.remove[0].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.remove[0].size()), Resize(1015, 375, 957, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	} else {
		if (dField.grave[0].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.grave[0].size()), Resize(870, 456, 1002, 461), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if (dField.remove[0].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.remove[0].size()), Resize(837, 375, 984, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	}
	if(dField.extra[1].size()) {
		int offset = (dField.extra[1].size() >= 10) ? 0 : mainGame->numFont->getDimension(dataManager.GetNumString(1)).Width;
        DrawShadowText(numFont, dataManager.GetNumString(dField.extra[1].size()), Resize(808 + offset, 207, 898, 232), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
        DrawShadowText(numFont, dataManager.GetNumString(dField.extra_p_count[1], true), Resize(828, 207, 918, 232), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
	if(dField.deck[1].size()) {
        DrawShadowText(numFont, dataManager.GetNumString(dField.deck[1].size()), Resize(465, 207, 481,232), Resize(1, 1, 1,1), 0xffffff00, 0xff000000, true, false, 0);
	}
	if (rule == 0) {
		if (dField.grave[1].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.grave[1].size()), Resize(420, 310, 462, 281), Resize(1, 1, 1,1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if (dField.remove[1].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.remove[1].size()), Resize(300, 310, 443, 340), Resize(1, 1, 1,1), 0xffffff00, 0xff000000, true, false, 0);
		}
	} else {
		if (dField.grave[1].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.grave[1].size()), Resize(455, 249, 462, 299), Resize(1, 1, 1,1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if (dField.remove[1].size()) {
            DrawShadowText(numFont, dataManager.GetNumString(dField.remove[1].size()), Resize(420, 310, 462, 281), Resize(1, 1, 1,1), 0xffffff00, 0xff000000, true, false, 0);
		}
	}
}
void Game::DrawStatus(ClientCard* pcard, int x1, int y1, int x2, int y2) {
	DrawShadowText(adFont, L"/", Resize(x1 - 3, y1 + 1, x1 + 5, y1 + 21), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);
	int w = adFont->getDimension(pcard->atkstring).Width;
	DrawShadowText(adFont, pcard->atkstring, Resize(x1 - 4, y1 + 1, x1 - 4, y1 + 21, -w, 0, 0, 0), Resize(1, 1, 1, 1),
		pcard->attack > pcard->base_attack ? 0xffffff00 : pcard->attack < pcard->base_attack ? 0xffff2090 : 0xffffffff, 0xff000000);
	if(pcard->type & TYPE_LINK) {
		w = adFont->getDimension(pcard->linkstring).Width;
		DrawShadowText(adFont, pcard->linkstring, Resize(x1 + 5, y1 + 1, x1 + 5, y1 + 21, 0, 0, w, 0), Resize(1, 1, 1, 1), 0xff99ffff);
	} else {
		w = adFont->getDimension(pcard->defstring).Width;
		DrawShadowText(adFont, pcard->defstring, Resize(x1 + 5, y1 + 1, x1 + 5, y1 + 21, 0, 0, w, 0), Resize(1, 1, 1, 1),
			pcard->defense > pcard->base_defense ? 0xffffff00 : pcard->defense < pcard->base_defense ? 0xffff2090 : 0xffffffff);
		DrawShadowText(adFont, pcard->lvstring, Resize(x2 + 1, y2, x2 + 3, y2 + 21), Resize(1, 1, 1, 1),
			(pcard->type & TYPE_XYZ) ? 0xffff80ff : (pcard->type & TYPE_TUNER) ? 0xffffff00 : 0xffffffff);
	}
}
void Game::DrawGUI() {
	while (imageLoading.size()) {
		auto mit = imageLoading.cbegin();
		mit->first->setImage(imageManager.GetTexture(mit->second));
		imageLoading.erase(mit);
	}
	for(auto fit = fadingList.begin(); fit != fadingList.end();) {
		auto fthis = fit++;
		FadingUnit& fu = *fthis;
		if(fu.fadingFrame) {
			fu.guiFading->setVisible(true);
			if(fu.isFadein) {
				if(fu.fadingFrame > 5) {
					fu.fadingUL.X -= fu.fadingDiff.X;
					fu.fadingLR.X += fu.fadingDiff.X;
					fu.fadingFrame--;
					fu.guiFading->setRelativePosition(irr::core::recti(fu.fadingUL, fu.fadingLR));
				} else {
					fu.fadingUL.Y -= fu.fadingDiff.Y;
					fu.fadingLR.Y += fu.fadingDiff.Y;
					fu.fadingFrame--;
					if(!fu.fadingFrame) {
						fu.guiFading->setRelativePosition(fu.fadingSize);
						if(fu.guiFading == wPosSelect) {
							btnPSAU->setDrawImage(true);
							btnPSAD->setDrawImage(true);
							btnPSDU->setDrawImage(true);
							btnPSDD->setDrawImage(true);
						}
						if(fu.guiFading == wCardSelect) {
							for(int i = 0; i < 5; ++i)
								btnCardSelect[i]->setDrawImage(true);
						}
						if(fu.guiFading == wCardDisplay) {
							for(int i = 0; i < 5; ++i)
								btnCardDisplay[i]->setDrawImage(true);
						}
						env->setFocus(fu.guiFading);
					} else
						fu.guiFading->setRelativePosition(irr::core::recti(fu.fadingUL, fu.fadingLR));
				}
			} else {
				if(fu.fadingFrame > 5) {
					fu.fadingUL.Y += fu.fadingDiff.Y;
					fu.fadingLR.Y -= fu.fadingDiff.Y;
					fu.fadingFrame--;
					fu.guiFading->setRelativePosition(irr::core::recti(fu.fadingUL, fu.fadingLR));
				} else {
					fu.fadingUL.X += fu.fadingDiff.X;
					fu.fadingLR.X -= fu.fadingDiff.X;
					fu.fadingFrame--;
					if(!fu.fadingFrame) {
						fu.guiFading->setVisible(false);
						fu.guiFading->setRelativePosition(fu.fadingSize);
						if(fu.guiFading == wPosSelect) {
							btnPSAU->setDrawImage(true);
							btnPSAD->setDrawImage(true);
							btnPSDU->setDrawImage(true);
							btnPSDD->setDrawImage(true);
						}
						if(fu.guiFading == wCardSelect) {
							for(int i = 0; i < 5; ++i)
								btnCardSelect[i]->setDrawImage(true);
						}
						if(fu.guiFading == wCardDisplay) {
							for(int i = 0; i < 5; ++i)
								btnCardDisplay[i]->setDrawImage(true);
						}
					} else
						fu.guiFading->setRelativePosition(irr::core::recti(fu.fadingUL, fu.fadingLR));
				}
				if(fu.signalAction && !fu.fadingFrame) {
					DuelClient::SendResponse();
					fu.signalAction = false;
				}
			}
		} else if(fu.autoFadeoutFrame) {
			fu.autoFadeoutFrame--;
			if(!fu.autoFadeoutFrame)
				HideElement(fu.guiFading);
		} else
			fadingList.erase(fthis);
	}
	env->drawAll();
}
void Game::DrawSpec() {
	if(showcard) {
	    irr::video::ITexture* showimg = imageManager.GetTexture(showcardcode);
    	if(showimg == NULL)
    		return;
        irr::core::dimension2d<irr::u32> orisize = showimg->getOriginalSize();
		switch(showcard) {
		case 1: {//show activiting effect
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tMask, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale - (CARD_IMG_WIDTH / 2) * yScale + (showcarddif > CARD_IMG_WIDTH ? CARD_IMG_WIDTH : showcarddif) * yScale, (150 + CARD_IMG_HEIGHT) * yScale),
                                irr::core::recti(CARD_IMG_HEIGHT - showcarddif, 0, CARD_IMG_HEIGHT - (showcarddif > CARD_IMG_WIDTH ? showcarddif - CARD_IMG_WIDTH : 0), CARD_IMG_HEIGHT), 0, 0, true);
			showcarddif += 15;
			if(showcarddif >= CARD_IMG_HEIGHT) {
				showcard = 2;
				showcarddif = 0;
			}
			break;
		}
		case 2: {
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tMask, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale + showcarddif * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale),
                                irr::core::recti(0, 0, CARD_IMG_WIDTH - showcarddif, CARD_IMG_HEIGHT), 0, 0, true);
			showcarddif += 15;
			if(showcarddif >= CARD_IMG_WIDTH) {
				showcard = 0;
			}
			break;
		}
		case 3: {//show negating effect
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tNegated, irr::core::recti(660 * xScale - 130 * yScale + showcarddif * yScale, (141 + showcarddif) * yScale, 660 * xScale + 130 * yScale - showcarddif * yScale, (397 - showcarddif) * yScale), irr::core::recti(0, 0, 128, 128), 0, 0, true);
			if(showcarddif < 64)
				showcarddif += 4;
			break;
		}
		case 4: {
			matManager.c2d[0] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[1] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[2] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[3] = (showcarddif << 24) | 0xffffff;
			driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 154 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, 404 * yScale),
                                irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, matManager.c2d, true);
			if(showcarddif < 255)
				showcarddif += 17;
			break;
		}
		case 5: {//show card special summoning
			matManager.c2d[0] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[1] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[2] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[3] = (showcarddif << 25) | 0xffffff;
			driver->draw2DImage(showimg, irr::core::recti(660 * xScale - showcarddif * 0.69685f * yScale, (277 - showcarddif) * yScale, 660 * xScale + showcarddif * 0.69685f * yScale, (277 + showcarddif) * yScale),
                                irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, matManager.c2d, true);
			if(showcarddif < 127)
				showcarddif += 9;
			break;
		}
		case 6: {//show time counter
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tNumber, irr::core::recti(660 * xScale - 130 * yScale + showcarddif * yScale, (141 + showcarddif) * yScale, 660 * xScale + 130 * yScale - showcarddif * yScale, (397 - showcarddif) * yScale),
			                    irr::core::recti((showcardp % 5) * 64, (showcardp / 5) * 64, (showcardp % 5 + 1) * 64, (showcardp / 5 + 1) * 64), 0, 0, true);
			if(showcarddif < 64)
				showcarddif += 4;
			break;
		}
		case 7: {//show normal summoning
			irr::core::vector2d<irr::s32> corner[4];
			float y = sin(showcarddif * 3.1415926f / 180.0f) * CARD_IMG_HEIGHT * mainGame->yScale;
			corner[0] = irr::core::vector2d<irr::s32>(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale - (CARD_IMG_HEIGHT * mainGame->yScale - y) * 0.3f , 404 * mainGame->yScale - y);
			corner[1] = irr::core::vector2d<irr::s32>(660 * xScale + (CARD_IMG_WIDTH / 2) * yScale + (CARD_IMG_HEIGHT * mainGame->yScale - y) * 0.3f , 404 * mainGame->yScale - y);
			corner[2] = irr::core::vector2d<irr::s32>(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 404 * mainGame->yScale);
			corner[3] = irr::core::vector2d<irr::s32>(660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, 404 * mainGame->yScale);
			irr::gui::Draw2DImageQuad(driver, showimg, irr::core::rect<irr::s32>(0, 0, orisize.Width, orisize.Height), corner);
			showcardp++;
			showcarddif += 9;
			if(showcarddif >= 90)
				showcarddif = 90;
			if(showcardp == 60) {
				showcardp = 0;
				showcarddif = 0;
			}
			break;
		}
		case 100: {//show finger-guessing 3 buttons
			if(showcardp < 60) {
				driver->draw2DImage(imageManager.tHand[(showcardcode >> 16) & 0x3], Resize(615, showcarddif, 615 + 89, 128 + showcarddif), irr::core::recti(0, 0, 89, 128), 0, 0, true);
				driver->draw2DImage(imageManager.tHand[showcardcode & 0x3], Resize(615, 540 - showcarddif, 615 + 89, 128 + 540 - showcarddif), irr::core::recti(0, 0, 89, 128), 0, 0, true);
				float dy = -0.333333f * showcardp + 10;
				showcardp++;
				if(showcardp < 30)
					showcarddif += (int)dy;
			} else
				showcard = 0;
			break;
		}
		case 101: {
			const wchar_t* lstr = L"";
			switch(showcardcode) {
			case 1:
				lstr = L"You Win!";
				break;
			case 2:
				lstr = L"You Lose!";
				break;
			case 3:
				lstr = L"Draw Game";
				break;
			case 4:
				lstr = L"Draw Phase";
				break;
			case 5:
				lstr = L"Standby Phase";
				break;
			case 6:
				lstr = L"Main Phase 1";
				break;
			case 7:
				lstr = L"Battle Phase";
				break;
			case 8:
				lstr = L"Main Phase 2";
				break;
			case 9:
				lstr = L"End Phase";
				break;
			case 10:
				lstr = L"Next Players Turn";
				break;
			case 11:
				lstr = L"Duel Start";
				break;
			case 12:
				lstr = L"Duel1 Start";
				break;
			case 13:
				lstr = L"Duel2 Start";
				break;
			case 14:
				lstr = L"Duel3 Start";
				break;
			}
			auto pos = lpcFont->getDimension(lstr);
			if(showcardp < 10) {
				int alpha = (showcardp * 25) << 24;
				DrawShadowText(lpcFont, lstr, ResizePhaseHint(660 - (9 - showcardp) * 40, 290, 960, 370, pos.Width), Resize(-1, -1, 0, 0), alpha | 0xffffff, alpha);
			} else if(showcardp < showcarddif) {
				DrawShadowText(lpcFont, lstr, ResizePhaseHint(660, 290, 960, 370, pos.Width), Resize(-1, -1, 0, 0), 0xffffffff);
				if(dInfo.vic_string.size() && (showcardcode == 1 || showcardcode == 2)) {
					int w = guiFont->getDimension(dInfo.vic_string).Width;
					if(w < 200)
						w = 200;
					driver->draw2DRectangle(0xa0000000, ResizeWin(640 - w / 2, 320, 690 + w / 2, 340));
					DrawShadowText(guiFont, dInfo.vic_string, ResizeWin(640 - w / 2, 320, 690 + w / 2, 340), Resize(-2, -1, 0, 0), 0xffffffff, 0xff000000, true, true, 0);
				}
			} else if(showcardp < showcarddif + 10) {
				int alpha = ((showcarddif + 10 - showcardp) * 25) << 24;
				DrawShadowText(lpcFont, lstr, ResizePhaseHint(660 + (showcardp - showcarddif) * 40, 290, 960, 370, pos.Width), Resize(-1, -1, 0, 0), alpha | 0xffffff, alpha);
			}
			showcardp++;
			break;
		}
		}
	}
	if(is_attacking) {
		irr::core::matrix4 matk;
		matk.setTranslation(atk_t);
		matk.setRotationRadians(atk_r);
		driver->setTransform(irr::video::ETS_WORLD, matk);
		driver->setMaterial(matManager.mATK);
		driver->drawVertexPrimitiveList(&matManager.vArrow[attack_sv], 12, matManager.iArrow, 10, irr::video::EVT_STANDARD, irr::scene::EPT_TRIANGLE_STRIP);
		attack_sv += 4;
		if (attack_sv > 28)
			attack_sv = 0;
	}
	bool showChat = true;
	if(hideChat) {
	    showChat = false;
	    hideChatTimer = 10;
	} else if(hideChatTimer > 0) {
	    showChat = false;
	    hideChatTimer--;
	}
	int chatRectY = 0;
	for(int i = 0; i < 8; ++i) {
		static unsigned int chatColor[] = {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xff8080ff, 0xffff4040, 0xffff4040,
		                                   0xffff4040, 0xff40ff40, 0xff4040ff, 0xff40ffff, 0xffff40ff, 0xffffff40, 0xffffffff, 0xff808080, 0xff404040};
		if(chatTiming[i]) {
			chatTiming[i]--;
			if(!is_building) {
				if(dInfo.isStarted && i >= 5)
					continue;
				if(!showChat && i > 2)
					continue;
			}

			int x = wChat->getRelativePosition().UpperLeftCorner.X;
			int y = (GAME_HEIGHT - 25) * mainGame->yScale;
			int maxwidth = 705 * xScale;
			if(is_building) {
				x = 810 * xScale;
				maxwidth = 205 * xScale;
			}

			std::wstring msg = SetStaticText(nullptr, maxwidth, guiFont, chatMsg[i].c_str());
			int w = guiFont->getDimension(msg).Width;
			int h = guiFont->getDimension(msg).Height + 2;

			irr::core::recti rectloc(x, y - chatRectY - h, x + 2 + w, y - chatRectY);
			irr::core::recti msgloc(x, y - chatRectY - h, x - 4, y - chatRectY);
			irr::core::recti shadowloc = msgloc + irr::core::vector2di(1, 1);

			driver->draw2DRectangle(rectloc, 0xa0000000, 0xa0000000, 0xa0000000, 0xa0000000);
			guiFont->drawUstring(msg, msgloc, 0xff000000, false, false);
			guiFont->drawUstring(msg, shadowloc, chatColor[chatType[i]], false, false);

			chatRectY += h;
		}
	}
}
void Game::DrawBackImage(irr::video::ITexture* texture) {
	if(!texture)
		return;
	driver->draw2DImage(texture, Resize(0, 0, GAME_WIDTH, GAME_HEIGHT), irr::core::recti(0, 0, texture->getOriginalSize().Width, texture->getOriginalSize().Height));
}
void Game::ShowElement(irr::gui::IGUIElement * win, int autoframe) {
	FadingUnit fu;
	fu.fadingSize = win->getRelativePosition();
	for(auto fit = fadingList.begin(); fit != fadingList.end(); ++fit)
		if(win == fit->guiFading && win != wOptions && win != wANNumber) // the size of wOptions is always setted by ClientField::ShowSelectOption before showing it
			fu.fadingSize = fit->fadingSize;
	irr::core::vector2di center = fu.fadingSize.getCenter();
	fu.fadingDiff.X = fu.fadingSize.getWidth() / 10;
	fu.fadingDiff.Y = (fu.fadingSize.getHeight() - 4) / 10;
	fu.fadingUL = center;
	fu.fadingLR = center;
	fu.fadingUL.Y -= 2;
	fu.fadingLR.Y += 2;
	fu.guiFading = win;
	fu.isFadein = true;
	fu.fadingFrame = 10;
	fu.autoFadeoutFrame = autoframe;
	fu.signalAction = 0;
	if(win == wPosSelect) {
		btnPSAU->setDrawImage(false);
		btnPSAD->setDrawImage(false);
		btnPSDU->setDrawImage(false);
		btnPSDD->setDrawImage(false);
	}
	if(win == wCardSelect) {
		for(int i = 0; i < 5; ++i)
			btnCardSelect[i]->setDrawImage(false);
	}
	if(win == wCardDisplay) {
		for(int i = 0; i < 5; ++i)
			btnCardDisplay[i]->setDrawImage(false);
	}
	win->setRelativePosition(irr::core::recti(center.X, center.Y, 0, 0));
	win->setVisible(true);
	fadingList.push_back(fu);
}
void Game::HideElement(irr::gui::IGUIElement * win, bool set_action) {
	if(!win->isVisible() && !set_action)
		return;
	FadingUnit fu;
	fu.fadingSize = win->getRelativePosition();
	for(auto fit = fadingList.begin(); fit != fadingList.end(); ++fit)
		if(win == fit->guiFading)
			fu.fadingSize = fit->fadingSize;
	fu.fadingDiff.X = fu.fadingSize.getWidth() / 10;
	fu.fadingDiff.Y = (fu.fadingSize.getHeight() - 4) / 10;
	fu.fadingUL = fu.fadingSize.UpperLeftCorner;
	fu.fadingLR = fu.fadingSize.LowerRightCorner;
	fu.guiFading = win;
	fu.isFadein = false;
	fu.fadingFrame = 10;
	fu.autoFadeoutFrame = 0;
	fu.signalAction = set_action;
	if(win == wPosSelect) {
		btnPSAU->setDrawImage(false);
		btnPSAD->setDrawImage(false);
		btnPSDU->setDrawImage(false);
		btnPSDD->setDrawImage(false);
	}
	if(win == wCardSelect) {
		for(int i = 0; i < 5; ++i)
			btnCardSelect[i]->setDrawImage(false);
		dField.conti_selecting = false;
		stCardListTip->setVisible(false);
		for(auto& pcard : dField.selectable_cards)
			dField.SetShowMark(pcard, false);
	}
	if(win == wCardDisplay) {
		for(int i = 0; i < 5; ++i)
			btnCardDisplay[i]->setDrawImage(false);
		stCardListTip->setVisible(false);
		for(auto& pcard : dField.display_cards)
			dField.SetShowMark(pcard, false);
	}
	fadingList.push_back(fu);
}
void Game::PopupElement(irr::gui::IGUIElement * element, int hideframe) {
	mainGame->soundManager->PlayDialogSound(element);
	element->getParent()->bringToFront(element);
	if(!is_building)
		dField.panel = element;
	env->setFocus(element);
	if(!hideframe)
		ShowElement(element);
	else ShowElement(element, hideframe);
}
void Game::WaitFrameSignal(int frame) {
	frameSignal.Reset();
	signalFrame = (gameConf.quick_animation && frame >= 12) ? 12 : frame;
	frameSignal.Wait();
}
void Game::DrawThumb(code_pointer cp, irr::core::vector2di pos, const LFList* lflist, bool drag) {
	auto code = cp->first;
	auto lcode = cp->second.alias;
	if(lcode == 0)
		lcode = code;
	irr::video::ITexture* img = imageManager.GetTexture(code);
	if(img == nullptr)
		return; //nullptr->getSize() will cause a crash
    irr::core::dimension2d<irr::u32> size = img->getOriginalSize();
    //mid pixel = (x2 - x1) / 2;
    float x1 = pos.X + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2;//reset position of left-half card
    float x2 = pos.X + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2;//reset position of right-half card
	irr::core::recti dragloc = irr::core::recti(x1, pos.Y, x2, pos.Y + CARD_THUMB_HEIGHT * mainGame->yScale);
	irr::core::recti limitloc = irr::core::recti(x1, pos.Y, x1 + 20 * mainGame->yScale, pos.Y + 20 * mainGame->yScale);
	irr::core::recti otloc = irr::core::recti(x1, pos.Y + 50 * mainGame->yScale, x1 + 30 * mainGame->yScale, pos.Y + 65 * mainGame->yScale);

	driver->draw2DImage(img, dragloc, irr::core::rect<irr::s32>(0, 0, size.Width, size.Height));
	auto lfit = lflist->content.find(lcode);
	if (lfit != lflist->content.end()) {
		switch(lfit->second) {
		case 0:
			driver->draw2DImage(imageManager.tLim, limitloc, irr::core::recti(0, 0, 64, 64), 0, 0, true);
			break;
		case 1:
			driver->draw2DImage(imageManager.tLim, limitloc, irr::core::recti(64, 0, 128, 64), 0, 0, true);
			break;
		case 2:
			driver->draw2DImage(imageManager.tLim, limitloc, irr::core::recti(0, 64, 64, 128), 0, 0, true);
			break;
		}
	}
	bool showAvail = false;
	bool showNotAvail = false;
	int filter_lm = cbLimit->getSelected();
	bool avail = !((filter_lm == 4 && !(cp->second.ot & AVAIL_OCG)
				|| (filter_lm == 5 && !(cp->second.ot & AVAIL_TCG))
				|| (filter_lm == 6 && !(cp->second.ot & AVAIL_SC))
				|| (filter_lm == 7 && !(cp->second.ot & AVAIL_CUSTOM))
				|| (filter_lm == 8 && (cp->second.ot & AVAIL_OCGTCG) != AVAIL_OCGTCG)));
	if(filter_lm >= 4) {
		showAvail = avail;
		showNotAvail = !avail;
	} else if(!(cp->second.ot & gameConf.defaultOT)) {
		showNotAvail = true;
	}
	if(showAvail) {
		if((cp->second.ot & AVAIL_OCG) && !(cp->second.ot & AVAIL_TCG))
			driver->draw2DImage(imageManager.tOT, otloc, irr::core::recti(0, 128, 128, 192), 0, 0, true);
		else if((cp->second.ot & AVAIL_TCG) && !(cp->second.ot & AVAIL_OCG))
			driver->draw2DImage(imageManager.tOT, otloc, irr::core::recti(0, 192, 128, 256), 0, 0, true);
	} else if(showNotAvail) {
		if(cp->second.ot & AVAIL_OCG)
			driver->draw2DImage(imageManager.tOT, otloc, irr::core::recti(0, 0, 128, 64), 0, 0, true);
		else if(cp->second.ot & AVAIL_TCG)
			driver->draw2DImage(imageManager.tOT, otloc, irr::core::recti(0, 64, 128, 128), 0, 0, true);
		else if(!avail)
			driver->draw2DImage(imageManager.tLim, otloc, irr::core::recti(0, 0, 64, 64), 0, 0, true);
	}
}
void Game::DrawDeckBd() {
	wchar_t textBuffer[64];
	//main deck
	int mainsize = deckManager.current_deck.main.size();
	driver->draw2DRectangle(Resize(310, 137, 410, 157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(Resize(309, 136, 410, 157));
    DrawShadowText(guiFont, dataManager.GetSysString(deckBuilder.showing_pack ? 1477 : 1330), Resize(300, 136, 395, 156), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
    DrawShadowText(numFont, dataManager.GetNumString(mainsize), Resize(360, 137, 420, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	driver->draw2DRectangle(Resize(310, 160, 797, deckBuilder.showing_pack ? 630 : 436), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(Resize(309, 159, 798, deckBuilder.showing_pack ? 630 : 436));
	//type count 2DRectangle
	driver->draw2DRectangle(Resize(638, 137, 798, 157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(Resize(637, 136, 798, 157));
	//monster count
	driver->draw2DImage(imageManager.tCardType, Resize(645, 136, 645+14+3/8, 156), irr::core::recti(0, 0, 23, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_MONSTER)), Resize(670, 137, 690, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	//spell count
	driver->draw2DImage(imageManager.tCardType, Resize(695, 136, 695+14+3/8, 156), irr::core::recti(23, 0, 46, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_SPELL)), Resize(720, 138, 740, 158), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
    //trap count
	driver->draw2DImage(imageManager.tCardType, Resize(745, 136, 745+14+3/8, 156), irr::core::recti(46, 0, 69, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_TRAP)), Resize(770, 137, 790, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	int lx;
	int dy = 68;
	float dx;
	if(mainsize <= 40) {
		dx = 436.0f / 9;
		lx = 10;
	} else if(deckBuilder.showing_pack) {
		lx = 10;
		if(mainsize > 10 * 7)
			lx = 11;
		if(mainsize > 11 * 7)
			lx = 12;
		dx = (mainGame->scrPackCards->isVisible() ? 414.0f : 436.0f) / (lx - 1);
		if(mainsize > 60)
			dy = 66;
	} else {
		lx = (mainsize - 41) / 4 + 11;
		dx = 436.0f / (lx - 1);
	}
	int padding = scrPackCards->getPos() * lx;
	for(int i = 0; i < mainsize - padding && i < 7 * lx; ++i) {
		int j = i + padding;
		DrawThumb(deckManager.current_deck.main[j], Resize(314 + (i % lx) * dx, 164 + (i / lx) * dy), deckBuilder.filterList);
		if(deckBuilder.hovered_pos == 1 && deckBuilder.hovered_seq == j)
			driver->draw2DRectangleOutline(irr::core::recti((313 + (i % lx) * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, (163 + (i / lx) * dy) * yScale, (313 + (i % lx) * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (163 + (i / lx) * dy + CARD_THUMB_HEIGHT + 1) * yScale));
	}
	if(!deckBuilder.showing_pack) {
		//extra deck
		driver->draw2DRectangle(Resize(310, 440, 410, 460), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 439, 410, 460));
        DrawShadowText(guiFont, dataManager.GetSysString(1331), Resize(300, 439, 395, 459), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.current_deck.extra.size()), Resize(360, 440, 420, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);
		driver->draw2DRectangle(Resize(310, 463, 797, 533), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 462, 798, 533));
		//type count 2DRectangle
		driver->draw2DRectangle(Resize(582, 440, 797, 460), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(581, 439, 798, 460));
		//fusion count
		driver->draw2DImage(imageManager.tCardType, Resize(595, 440, 595+14+3/8, 460), irr::core::recti(0, 32, 23, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_FUSION)), Resize(620, 440, 640, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		//synchro count
		driver->draw2DImage(imageManager.tCardType, Resize(645, 440, 645+14+3/8, 460), irr::core::recti(23, 32, 46, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_SYNCHRO)), Resize(670, 440, 690, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		//XYZ count
		driver->draw2DImage(imageManager.tCardType, Resize(695, 440, 695+14+3/8, 460), irr::core::recti(46, 32, 69, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_XYZ)), Resize(720, 440, 740, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		//link count
		driver->draw2DImage(imageManager.tCardType, Resize(745, 440, 745+14+3/8, 460), irr::core::recti(0, 64, 23, 96), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_LINK)), Resize(770, 440, 790, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		if(deckManager.current_deck.extra.size() <= 10)
			dx = 436.0f / 9;
		else dx = 436.0f / (deckManager.current_deck.extra.size() - 1);
		for(size_t i = 0; i < deckManager.current_deck.extra.size(); ++i) {
			DrawThumb(deckManager.current_deck.extra[i], Resize(314 + i * dx, 466), deckBuilder.filterList);
			if(deckBuilder.hovered_pos == 2 && deckBuilder.hovered_seq == (int)i)
				driver->draw2DRectangleOutline(irr::core::recti((313 + i * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, 465 * yScale, (313 + i * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (465 + CARD_THUMB_HEIGHT + 1 ) * yScale));
		}
		//side deck
		driver->draw2DRectangle(Resize(310, 537, 410, 557), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 536, 410, 557));
        DrawShadowText(guiFont, dataManager.GetSysString(1332), Resize(300, 536, 395, 556), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.current_deck.side.size()), Resize(360, 537, 420, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		driver->draw2DRectangle(Resize(310, 560, 797, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 559, 797, 630));
		//type count 2DRectangle
		driver->draw2DRectangle(Resize(638, 537, 797, 557), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(637, 536, 798, 557));
		//monster count
		driver->draw2DImage(imageManager.tCardType, Resize(645, 537, 645+14+3/8, 557), irr::core::recti(0, 0, 23, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_MONSTER)), Resize(670, 537, 690, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		//spell count
		driver->draw2DImage(imageManager.tCardType, Resize(695, 537, 695+14+3/8, 557), irr::core::recti(23, 0, 46, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_SPELL)), Resize(720, 537, 740, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	    //trap count
		driver->draw2DImage(imageManager.tCardType, Resize(745, 537, 745+14+3/8, 557), irr::core::recti(46, 0, 69, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_TRAP)), Resize(770, 537, 790, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
		if(deckManager.current_deck.side.size() <= 10)
			dx = 436.0f / 9;
		else dx = 436.0f / (deckManager.current_deck.side.size() - 1);
		for(size_t i = 0; i < deckManager.current_deck.side.size(); ++i) {
			DrawThumb(deckManager.current_deck.side[i], Resize(314 + i * dx, 564), deckBuilder.filterList);
			if(deckBuilder.hovered_pos == 3 && deckBuilder.hovered_seq == (int)i)
				driver->draw2DRectangleOutline(irr::core::recti((313 + i * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, 563 * yScale, (313 + i * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (563 + CARD_THUMB_HEIGHT + 1) * yScale));
		}
	}
	if(is_siding) {
		// side chat background
		driver->draw2DRectangle(Resize(806, 10, 1020, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 9, 1020, 630));
	} else {
		//search result
		driver->draw2DRectangle(Resize(806, 137, 930,157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 136, 930,157));
        DrawShadowText(guiFont, dataManager.GetSysString(1333),Resize(795, 136, 930,156),Resize(0, 1, 2, 0), 0xffffffff,0xff000000, true, false);
        DrawShadowText(numFont, deckBuilder.result_string,Resize(865, 136, 930,156),Resize(0, 1, 2, 0), 0xffffffff,0xff000000, true, false);
		driver->draw2DRectangle(Resize(806, 160, 1020, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 159, 1020, 630));
	}
	for(int i = 0; i < 9 && i + scrFilter->getPos() < (int)deckBuilder.results.size(); ++i) {
		code_pointer ptr = deckBuilder.results[i + scrFilter->getPos()];
		if(deckBuilder.hovered_pos == 4 && deckBuilder.hovered_seq == (int)i)
			driver->draw2DRectangle(0x80000000, Resize(806, 164 + i * 66, 1019, 230 + i * 66));
		DrawThumb(ptr, Resize(805, 165 + i * 66),deckBuilder.filterList);
		const wchar_t* availBuffer = L"";
		if ((ptr->second.ot & AVAIL_OCGTCG) == AVAIL_OCG)
			availBuffer = L" [OCG]";
		else if ((ptr->second.ot & AVAIL_OCGTCG) == AVAIL_TCG)
			availBuffer = L" [TCG]";
		else if ((ptr->second.ot & AVAIL_CUSTOM) == AVAIL_CUSTOM)
			availBuffer = L" [Custom]";
		if(ptr->second.type & TYPE_MONSTER) {
			myswprintf(textBuffer, L"%ls", dataManager.GetName(ptr->first));
            DrawShadowText(guiFont, textBuffer, Resize(850, 165 + i * 66, 1000, 185 + i * 66), Resize(1, 1, 0, 0));
			const wchar_t* form = L"\u2605";
			wchar_t adBuffer[32]{};
			wchar_t scaleBuffer[16]{};
			if(!(ptr->second.type & TYPE_LINK)) {
				if(ptr->second.type & TYPE_XYZ)
					form = L"\u2606";
				if(ptr->second.attack < 0 && ptr->second.defense < 0)
					myswprintf(adBuffer, L"?/?");
				else if(ptr->second.attack < 0)
					myswprintf(adBuffer, L"?/%d", ptr->second.defense);
				else if(ptr->second.defense < 0)
					myswprintf(adBuffer, L"%d/?", ptr->second.attack);
				else
					myswprintf(adBuffer, L"%d/%d", ptr->second.attack, ptr->second.defense);
			} else {
				form = L"LINK-";
				if(ptr->second.attack < 0)
					myswprintf(adBuffer, L"?/-");
				else
					myswprintf(adBuffer, L"%d/-", ptr->second.attack);
			}
			myswprintf(textBuffer, L"%ls/%ls %ls%d", dataManager.FormatAttribute(ptr->second.attribute).c_str(), dataManager.FormatRace(ptr->second.race).c_str(),
				form, ptr->second.level);
            DrawShadowText(guiFont, textBuffer, Resize(850, 186 + i * 66, 1000, 207 + i * 66), Resize(1, 1, 0, 0));
			if(ptr->second.type & TYPE_PENDULUM) {
				myswprintf(scaleBuffer, L" %d/%d", ptr->second.lscale, ptr->second.rscale);
			}
			myswprintf(textBuffer, L"%ls%ls%ls", adBuffer, scaleBuffer, availBuffer);
            DrawShadowText(guiFont, textBuffer, Resize(850, 209 + i * 66, 1000, 230 + i * 66), Resize(1, 1, 0, 0));
		} else {
			myswprintf(textBuffer, L"%ls", dataManager.GetName(ptr->first));
            DrawShadowText(guiFont, textBuffer, Resize(850, 164 + i * 66, 1000, 185 + i * 66), Resize(1, 1, 0, 0));
			myswprintf(textBuffer, L"%ls", dataManager.FormatType(ptr->second.type).c_str());
            DrawShadowText(guiFont, textBuffer, Resize(850, 186 + i * 66, 1000, 207 + i * 66), Resize(1, 1, 0, 0));
			myswprintf(textBuffer, L"%ls", availBuffer);
            DrawShadowText(textFont, textBuffer, Resize(850, 209 + i * 66, 1000, 230 + i * 66), Resize(1, 1 , 0, 0));
		}
	}
	if(deckBuilder.is_draging) {
		DrawThumb(deckBuilder.draging_pointer, irr::core::vector2di(deckBuilder.dragx - CARD_THUMB_WIDTH / 2 * mainGame->xScale, deckBuilder.dragy - CARD_THUMB_HEIGHT / 2 * mainGame->yScale), deckBuilder.filterList, true);
	}
}
}
