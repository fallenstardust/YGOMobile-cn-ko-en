#include "game.h"
#include "materials.h"
#include "image_manager.h"
#include "deck_manager.h"
#include "duelclient.h"
#include "../ocgcore/common.h"

namespace ygo {

inline void SetS3DVertex(S3DVertex* v, f32 x1, f32 y1, f32 x2, f32 y2, f32 z, f32 nz, f32 tu1, f32 tv1, f32 tu2, f32 tv2) {
	v[0] = S3DVertex(x1, y1, z, 0, 0, nz, SColor(255, 255, 255, 255), tu1, tv1);
	v[1] = S3DVertex(x2, y1, z, 0, 0, nz, SColor(255, 255, 255, 255), tu2, tv1);
	v[2] = S3DVertex(x1, y2, z, 0, 0, nz, SColor(255, 255, 255, 255), tu1, tv2);
	v[3] = S3DVertex(x2, y2, z, 0, 0, nz, SColor(255, 255, 255, 255), tu2, tv2);
}

void Game::DrawSelectionLine(irr::video::S3DVertex* vec, bool strip, int width, float* cv) {
#ifdef _IRR_ANDROID_PLATFORM_
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
#else
	if(!gameConf.use_d3d) {
		float origin[4] = {1.0f, 1.0f, 1.0f, 1.0f};
		glLineWidth(width);
		glLineStipple(1, linePattern);
		if(strip)
			glEnable(GL_LINE_STIPPLE);
		glDisable(GL_TEXTURE_2D);
		glMaterialfv(GL_FRONT, GL_AMBIENT, cv);
		glBegin(GL_LINE_LOOP);
		glVertex3fv((float*)&vec[0].Pos);
		glVertex3fv((float*)&vec[1].Pos);
		glVertex3fv((float*)&vec[3].Pos);
		glVertex3fv((float*)&vec[2].Pos);
		glEnd();
		glMaterialfv(GL_FRONT, GL_AMBIENT, origin);
		glDisable(GL_LINE_STIPPLE);
		glEnable(GL_TEXTURE_2D);
	} else {
		driver->setMaterial(matManager.mOutLine);
		if(strip) {
			if(linePattern < 15) {
				driver->draw3DLine(vec[0].Pos, vec[0].Pos + (vec[1].Pos - vec[0].Pos) * (linePattern + 1) / 15.0);
				driver->draw3DLine(vec[1].Pos, vec[1].Pos + (vec[3].Pos - vec[1].Pos) * (linePattern + 1) / 15.0);
				driver->draw3DLine(vec[3].Pos, vec[3].Pos + (vec[2].Pos - vec[3].Pos) * (linePattern + 1) / 15.0);
				driver->draw3DLine(vec[2].Pos, vec[2].Pos + (vec[0].Pos - vec[2].Pos) * (linePattern + 1) / 15.0);
			} else {
				driver->draw3DLine(vec[0].Pos + (vec[1].Pos - vec[0].Pos) * (linePattern - 14) / 15.0, vec[1].Pos);
				driver->draw3DLine(vec[1].Pos + (vec[3].Pos - vec[1].Pos) * (linePattern - 14) / 15.0, vec[3].Pos);
				driver->draw3DLine(vec[3].Pos + (vec[2].Pos - vec[3].Pos) * (linePattern - 14) / 15.0, vec[2].Pos);
				driver->draw3DLine(vec[2].Pos + (vec[0].Pos - vec[2].Pos) * (linePattern - 14) / 15.0, vec[0].Pos);
			}
		} else {
			driver->draw3DLine(vec[0].Pos, vec[1].Pos);
			driver->draw3DLine(vec[1].Pos, vec[3].Pos);
			driver->draw3DLine(vec[3].Pos, vec[2].Pos);
			driver->draw3DLine(vec[2].Pos, vec[0].Pos);
		}
	}
#endif
}

void Game::DrawSelectionLine(irr::gui::IGUIElement* element, int width, irr::video::SColor color) {
	recti pos = element->getAbsolutePosition();
	float x1 = pos.UpperLeftCorner.X;
	float x2 = pos.LowerRightCorner.X;
	float y1 = pos.UpperLeftCorner.Y;
	float y2 = pos.LowerRightCorner.Y;
	float w = pos.getWidth();
	float h = pos.getHeight();
	if(linePatternD3D < 15) {
		driver->draw2DRectangle(color, recti(x1 - 1 - width, y1 - 1 - width, x1 + (w * (linePatternD3D + 1) / 15.0) + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, recti(x2 - (w * (linePatternD3D + 1) / 15.0) - 1 - width, y2 + 1, x2 + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, recti(x1 - 1 - width, y1 - 1 - width, x1 - 1, y2 - (h * (linePatternD3D + 1) / 15.0) + 1 + width));
		driver->draw2DRectangle(color, recti(x2 + 1, y1 + (h * (linePatternD3D + 1) / 15.0) - 1 - width, x2 + 1 + width, y2 + 1 + width));
	} else {
		driver->draw2DRectangle(color, recti(x1 - 1 - width + (w * (linePatternD3D - 14) / 15.0), y1 - 1 - width, x2 + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, recti(x1 - 1 - width, y2 + 1, x2 - (w * (linePatternD3D - 14) / 15.0) + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, recti(x1 - 1 - width, y2 - (h * (linePatternD3D - 14) / 15.0) - 1 - width, x1 - 1, y2 + 1 + width));
		driver->draw2DRectangle(color, recti(x2 + 1, y1 - 1 - width, x2 + 1 + width, y1 + (h * (linePatternD3D - 14) / 15.0) + 1 + width));
	}
}
void Game::DrawBackGround() {
	static int selFieldAlpha = 255;
	static int selFieldDAlpha = -10;
//	matrix4 im = irr::core::IdentityMatrix;
//	im.setTranslation(vector3df(0, 0, -0.01f));
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
			ITexture* texture = imageManager.GetTextureField(fieldcode1);
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
			ITexture* texture = imageManager.GetTextureField(fieldcode);
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
			DrawShadowText(numFont, dInfo.str_total_attack[0], recti(430 * mainGame->xScale, 346 * mainGame->yScale, 445 * mainGame->xScale, 366 * mainGame->yScale), recti(0, 1, 2, 0), dInfo.total_attack_color[0], 0xff000000, true, false, 0);
	    } else {
			driver->drawVertexPrimitiveList(matManager.vTotalAtkmeT, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[0], recti(590 * mainGame->xScale, 326 * mainGame->yScale, 610 * mainGame->xScale, 346 * mainGame->yScale), recti(0, 1, 2, 0), dInfo.total_attack_color[0], 0xff000000, true, false, 0);
	    }
	}
	if (mainGame->dInfo.total_attack[1] > 0) {
		matManager.mTexture.setTexture(0, imageManager.tTotalAtk);
		driver->setMaterial(matManager.mTexture);
		if (dInfo.duel_rule >= 4) {
		    driver->drawVertexPrimitiveList(matManager.vTotalAtkop, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[1], recti(885 * mainGame->xScale, 271 * mainGame->yScale, 905 * mainGame->xScale, 291 * mainGame->yScale), recti(0, 1, 2, 0), dInfo.total_attack_color[1], 0xff000000, true, false, 0);
	    } else {
			driver->drawVertexPrimitiveList(matManager.vTotalAtkopT, 4, matManager.iRectangle, 2);
		    DrawShadowText(numFont, dInfo.str_total_attack[1], recti(740 * mainGame->xScale, 295 * mainGame->yScale, 760 * mainGame->xScale, 315 * mainGame->yScale), recti(0, 1, 2, 0), dInfo.total_attack_color[1], 0xff000000, true, false, 0);
		
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
		S3DVertex *vertex = 0;
		if (dField.hovered_location == LOCATION_DECK)
			vertex = matManager.vFieldDeck[dField.hovered_controler];
		else if (dField.hovered_location == LOCATION_MZONE) {
			vertex = matManager.vFieldMzone[dField.hovered_controler][dField.hovered_sequence];
			ClientCard* pcard = mainGame->dField.mzone[dField.hovered_controler][dField.hovered_sequence];
			if(pcard && pcard->type & TYPE_LINK) {
				DrawLinkedZones(pcard);
			}
		} else if (dField.hovered_location == LOCATION_SZONE)
			vertex = matManager.vFieldSzone[dField.hovered_controler][dField.hovered_sequence][rule];
		else if (dField.hovered_location == LOCATION_GRAVE)
			vertex = matManager.vFieldGrave[dField.hovered_controler][rule];
		else if (dField.hovered_location == LOCATION_REMOVED)
			vertex = matManager.vFieldRemove[dField.hovered_controler][rule];
		else if (dField.hovered_location == LOCATION_EXTRA)
			vertex = matManager.vFieldExtra[dField.hovered_controler];
		selFieldAlpha += selFieldDAlpha;
		if (selFieldAlpha <= 5) {
			selFieldAlpha = 5;
			selFieldDAlpha = 10;
		}
		if (selFieldAlpha >= 205) {
			selFieldAlpha = 205;
			selFieldDAlpha = -10;
		}
		S3DVertex v2[4];//fix the highlight grid
		SetS3DVertex(v2, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.001f, 1, 0, 0, 0, 0);//z(0.001f)
		matManager.mSelField.AmbientColor = 0xffffffff;
		matManager.mSelField.DiffuseColor = selFieldAlpha << 24;
		driver->setMaterial(matManager.mSelField);
		driver->drawVertexPrimitiveList(v2, 4, matManager.iRectangle, 2);
	}
}

void Game::DrawLinkedZones(ClientCard* pcard) {
	int mark = pcard->link_marker;
	S3DVertex *vertex = 0;
    S3DVertex vSelField[4];
	matManager.mSelField.AmbientColor = 0xff0261a2;
	driver->setMaterial(matManager.mSelField);
	if (dField.hovered_sequence<5) {
		if (mark & LINK_MARKER_LEFT && dField.hovered_sequence>0) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][dField.hovered_sequence - 1];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
        }
		if (mark & LINK_MARKER_RIGHT && dField.hovered_sequence<4) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][dField.hovered_sequence + 1];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (dInfo.duel_rule >= 4) {
		if ((mark & LINK_MARKER_TOP_LEFT && dField.hovered_sequence == 2) || (mark & LINK_MARKER_TOP && dField.hovered_sequence == 1) || (mark & LINK_MARKER_TOP_RIGHT && dField.hovered_sequence == 0)) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][5];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if ((mark & LINK_MARKER_TOP_LEFT && dField.hovered_sequence == 4) || (mark & LINK_MARKER_TOP && dField.hovered_sequence == 3) || (mark & LINK_MARKER_TOP_RIGHT && dField.hovered_sequence == 2)) {
		    vertex = matManager.vFieldMzone[dField.hovered_controler][6];
			SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
			driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
	}
	} else {
		int swap = (dField.hovered_sequence == 5) ? 0 : 2;
		if (mark & LINK_MARKER_BOTTOM_LEFT) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][0 + swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (mark & LINK_MARKER_BOTTOM) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][1 + swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (mark & LINK_MARKER_BOTTOM_RIGHT) {
		vertex = matManager.vFieldMzone[dField.hovered_controler][2 + swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (mark & LINK_MARKER_TOP_LEFT) {
		vertex = matManager.vFieldMzone[1 - dField.hovered_controler][4 - swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (mark & LINK_MARKER_TOP) {
		vertex = matManager.vFieldMzone[1 - dField.hovered_controler][3 - swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
		if (mark & LINK_MARKER_TOP_RIGHT) {
		vertex = matManager.vFieldMzone[1 - dField.hovered_controler][2 - swap];
		SetS3DVertex(vSelField, vertex[0].Pos.X, vertex[1].Pos.Y, vertex[3].Pos.X, vertex[2].Pos.Y, 0.01f, 1, 0, 0, 0, 0);
		driver->drawVertexPrimitiveList(vSelField, 4, matManager.iRectangle, 2);
		}
	}
}

void Game::DrawCards() {
	for(auto cit = dField.overlay_cards.begin(); cit != dField.overlay_cards.end(); ++cit)
		DrawCard(*cit);
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
		atk.setTranslation(pcard->curPos + vector3df(0, (pcard->controler == 0 ? -1 : 1) * (atkdy / 4.0f + 0.35f), 0.05f));
		atk.setRotationRadians(vector3df(0, 0, pcard->controler == 0 ? 0 : 3.1415926f));
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
void Game::DrawShadowText(CGUITTFont * font, const core::stringw & text, const core::rect<s32>& position, const core::rect<s32>& padding,
						  video::SColor color, video::SColor shadowcolor, bool hcenter, bool vcenter, const core::rect<s32>* clip) {
	core::rect<s32> shadowposition = recti(position.UpperLeftCorner.X - padding.UpperLeftCorner.X, position.UpperLeftCorner.Y - padding.UpperLeftCorner.Y, 
										   position.LowerRightCorner.X - padding.LowerRightCorner.X, position.LowerRightCorner.Y - padding.LowerRightCorner.Y);
	font->draw(text, shadowposition, shadowcolor, hcenter, vcenter, clip);
	font->draw(text, position, color, hcenter, vcenter, clip);
}
void Game::DrawMisc() {
	static irr::core::vector3df act_rot(0, 0, 0);
	int rule = (dInfo.duel_rule >= 4) ? 1 : 0;
	irr::core::matrix4 im, ic, it;
	act_rot.Z += 0.02f;
	im.setRotationRadians(act_rot);
	matManager.mTexture.setTexture(0, imageManager.tAct);
	driver->setMaterial(matManager.mTexture);
	if(dField.deck_act) {
		im.setTranslation(vector3df((matManager.vFieldDeck[0][0].Pos.X + matManager.vFieldDeck[0][1].Pos.X) / 2,
			(matManager.vFieldDeck[0][0].Pos.Y + matManager.vFieldDeck[0][2].Pos.Y) / 2, dField.deck[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.grave_act) {
		im.setTranslation(vector3df((matManager.vFieldGrave[0][rule][0].Pos.X + matManager.vFieldGrave[0][rule][1].Pos.X) / 2,
			(matManager.vFieldGrave[0][rule][0].Pos.Y + matManager.vFieldGrave[0][rule][2].Pos.Y) / 2, dField.grave[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.remove_act) {
		im.setTranslation(vector3df((matManager.vFieldRemove[0][rule][0].Pos.X + matManager.vFieldRemove[0][rule][1].Pos.X) / 2,
			(matManager.vFieldRemove[0][rule][0].Pos.Y + matManager.vFieldRemove[0][rule][2].Pos.Y) / 2, dField.remove[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.extra_act) {
		im.setTranslation(vector3df((matManager.vFieldExtra[0][0].Pos.X + matManager.vFieldExtra[0][1].Pos.X) / 2,
			(matManager.vFieldExtra[0][0].Pos.Y + matManager.vFieldExtra[0][2].Pos.Y) / 2, dField.extra[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.pzone_act[0]) {
		int seq = dInfo.duel_rule >= 4 ? 0 : 6;
		im.setTranslation(vector3df((matManager.vFieldSzone[0][seq][rule][0].Pos.X + matManager.vFieldSzone[0][seq][rule][1].Pos.X) / 2,
			(matManager.vFieldSzone[0][seq][rule][0].Pos.Y + matManager.vFieldSzone[0][seq][rule][2].Pos.Y) / 2, 0.03f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.pzone_act[1]) {
		int seq = dInfo.duel_rule >= 4 ? 0 : 6;
		im.setTranslation(vector3df((matManager.vFieldSzone[1][seq][rule][0].Pos.X + matManager.vFieldSzone[1][seq][rule][1].Pos.X) / 2,
			(matManager.vFieldSzone[1][seq][rule][0].Pos.Y + matManager.vFieldSzone[1][seq][rule][2].Pos.Y) / 2, 0.03f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.conti_act) {
		im.setTranslation(vector3df((matManager.vFieldContiAct[0].X + matManager.vFieldContiAct[1].X) / 2,
			(matManager.vFieldContiAct[0].Y + matManager.vFieldContiAct[2].Y) / 2, 0.03f));
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}
	if(dField.chains.size() > 1) {
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
			matManager.vChainNum[0].TCoords = vector2df(0.19375f * (i % 5), 0.2421875f * (i / 5));
			matManager.vChainNum[1].TCoords = vector2df(0.19375f * (i % 5 + 1), 0.2421875f * (i / 5));
			matManager.vChainNum[2].TCoords = vector2df(0.19375f * (i % 5), 0.2421875f * (i / 5 + 1));
			matManager.vChainNum[3].TCoords = vector2df(0.19375f * (i % 5 + 1), 0.2421875f * (i / 5 + 1));
			driver->setMaterial(matManager.mTRTexture);
			driver->setTransform(irr::video::ETS_WORLD, it);
			driver->drawVertexPrimitiveList(matManager.vChainNum, 4, matManager.iRectangle, 2);
		}
	}
	//finish button
	if(btnCancelOrFinish->isVisible())
		DrawSelectionLine(btnCancelOrFinish, 4, 0xff00ff00);
	//lp bar
	//driver->draw2DImage(imageManager.tLPFrame, recti(400 * mainGame->xScale, 10 * mainGame->yScale, 629 * mainGame->xScale, 30 * mainGame->yScale), recti(0, 0, 200, 20), 0, 0, true);
	//driver->draw2DImage(imageManager.tLPFrame, recti(691 * mainGame->xScale, 10 * mainGame->yScale, 920 * mainGame->xScale, 30 * mainGame->yScale), recti(0, 0, 200, 20), 0, 0, true);
	if(dInfo.lp[0] >= 8000)
		driver->draw2DImage(imageManager.tLPBar, recti(390 * mainGame->xScale, 12 * mainGame->yScale, 625 * mainGame->xScale, 74 * mainGame->yScale), recti(0, 0, 60, 60), 0, 0, true);
	else driver->draw2DImage(imageManager.tLPBar, recti(390 * mainGame->xScale, 12 * mainGame->yScale, (390 + 235 * dInfo.lp[0] / 8000) * mainGame->xScale, 74 * mainGame->yScale), recti(0, 0, 60, 60), 0, 0, true);
	if(dInfo.lp[1] >= 8000)
		driver->draw2DImage(imageManager.tLPBar, recti(695 * mainGame->xScale, 12 * mainGame->yScale, 930 * mainGame->xScale, 74 * mainGame->yScale), recti(0, 0, 60, 60), 0, 0, true);
	else driver->draw2DImage(imageManager.tLPBar, recti((930 - 235 * dInfo.lp[1] / 8000) * mainGame->xScale, 12 * mainGame->yScale, 930 * mainGame->xScale, 74 * mainGame->yScale), recti(0, 0, 60, 60), 0, 0, true);
	if(lpframe) {
		dInfo.lp[lpplayer] -= lpd;
		myswprintf(dInfo.strLP[lpplayer], L"%d", dInfo.lp[lpplayer]);
		lpccolor -= 0x19000000;
		lpframe--;
	}
	if(lpcstring) {
		if(lpplayer == 0) {
			lpcFont->draw(lpcstring, recti(400 * mainGame->xScale, 470 * mainGame->yScale, 920 * mainGame->xScale, 520 * mainGame->yScale), lpccolor | 0x00ffffff, true, false, 0);
			lpcFont->draw(lpcstring, recti(400 * mainGame->xScale, 472 * mainGame->yScale, 922 * mainGame->xScale, 520 * mainGame->yScale), lpccolor, true, false, 0);
		} else {
			lpcFont->draw(lpcstring, recti(400 * mainGame->xScale, 160 * mainGame->yScale, 920 * mainGame->xScale, 210 * mainGame->yScale), lpccolor | 0x00ffffff, true, false, 0);
			lpcFont->draw(lpcstring, recti(400 * mainGame->xScale, 162 * mainGame->yScale, 922 * mainGame->xScale, 210 * mainGame->yScale), lpccolor, true, false, 0);
		}
	}
	//avatar image
	driver->draw2DImage(imageManager.tAvatar[0], recti(335 * mainGame->xScale, 15 * mainGame->yScale, 390 * mainGame->xScale, 70 * mainGame->yScale), recti(0, 0, 128, 128), 0, 0, true);
	driver->draw2DImage(imageManager.tAvatar[1], recti(930 * mainGame->xScale, 15 * mainGame->yScale, 985 * mainGame->xScale, 70 * mainGame->yScale), recti(0, 0, 128, 128), 0, 0, true);
	if((dInfo.turn % 2 && dInfo.isFirst) || (!(dInfo.turn % 2) && !dInfo.isFirst)) {
		driver->draw2DImage(imageManager.tLPBarFrame, recti(327 * mainGame->xScale, 8 * mainGame->yScale, 630 * mainGame->xScale, 78 * mainGame->yScale), recti(0, 0, 305, 70), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, recti(689 * mainGame->xScale, 8 * mainGame->yScale, 991 * mainGame->xScale, 78 * mainGame->yScale), recti(0, 210, 305, 280), 0, 0, true);
		//driver->draw2DRectangle(0xa0000000, recti(327 * mainGame->xScale, 8 * mainGame->yScale, 630 * mainGame->xScale, 51 * mainGame->yScale));
		//driver->draw2DRectangleOutline(recti(327 * mainGame->xScale, 8 * mainGame->yScale, 630 * mainGame->xScale, 51 * mainGame->yScale), 0xffff8080);
	} else {
		driver->draw2DImage(imageManager.tLPBarFrame, recti(327 * mainGame->xScale, 8 * mainGame->yScale, 630 * mainGame->xScale, 78 * mainGame->yScale), recti(0, 70, 305, 140), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, recti(689 * mainGame->xScale, 8 * mainGame->yScale, 991 * mainGame->xScale, 78 * mainGame->yScale), recti(0, 140, 305, 210), 0, 0, true);
		//driver->draw2DRectangle(0xa0000000, recti(689 * mainGame->xScale, 8 * mainGame->yScale, 991 * mainGame->xScale, 51 * mainGame->yScale));
		//driver->draw2DRectangleOutline(recti(689 * mainGame->xScale, 8 * mainGame->yScale, 991 * mainGame->xScale, 51 * mainGame->yScale), 0xffff8080);
	}
	//Time Display
	if(!dInfo.isReplay && dInfo.player_type < 7 && dInfo.time_limit) {
		if(imageManager.tClock) {
			driver->draw2DImage(imageManager.tClock, recti(577 * mainGame->xScale, 50 * mainGame->yScale, 595 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 0, 34, 34), 0, 0, true);
			driver->draw2DImage(imageManager.tClock, recti(695 * mainGame->xScale, 50 * mainGame->yScale, 713 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 0, 34, 34), 0, 0, true);
		}
		DrawShadowText(numFont, dInfo.str_time_left[0], recti(595 * mainGame->xScale, 49 * mainGame->yScale, 625 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.time_color[0], 0xff000000, true, false, 0);
		DrawShadowText(numFont, dInfo.str_time_left[1], recti(713 * mainGame->xScale, 49 * mainGame->yScale, 743 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.time_color[1], 0xff000000, true, false, 0);

		driver->draw2DImage(imageManager.tCover[0], recti(537 * mainGame->xScale, 51 * mainGame->yScale, 550 * mainGame->xScale, 70 * mainGame->yScale), rect<s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[1], recti(745 * mainGame->xScale, 51 * mainGame->yScale, 758 * mainGame->xScale, 70 * mainGame->yScale), rect<s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], recti(550 * mainGame->xScale, 49 * mainGame->yScale, 575 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.card_count_color[0], 0xff000000, true, false, 0);
		DrawShadowText(numFont, dInfo.str_card_count[1], recti(757 * mainGame->xScale, 49 * mainGame->yScale, 782 * mainGame->xScale, 68 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.card_count_color[1], 0xff000000, true, false, 0);
		
		/*
		driver->draw2DRectangle(recti(525 * mainGame->xScale, 34 * mainGame->yScale, (525 + dInfo.time_left[0] * 100 / dInfo.time_limit) * mainGame->xScale, 44 * mainGame->yScale), 0xa0e0e0e0, 0xa0e0e0e0, 0xa0c0c0c0, 0xa0c0c0c0);
		driver->draw2DRectangleOutline(recti(525 * mainGame->xScale, 34 * mainGame->yScale, 625 * mainGame->xScale, 44 * mainGame->yScale), 0xffffffff);
		driver->draw2DRectangle(recti((795 - dInfo.time_left[1] * 100 / dInfo.time_limit) * mainGame->xScale, 34 * mainGame->yScale, 795 * mainGame->xScale, 44 * mainGame->yScale), 0xa0e0e0e0, 0xa0e0e0e0, 0xa0c0c0c0, 0xa0c0c0c0);
		driver->draw2DRectangleOutline(recti(695 * mainGame->xScale, 34 * mainGame->yScale, 795 * mainGame->xScale, 44 * mainGame->yScale), 0xffffffff);
		*/
	}
	else {
		driver->draw2DImage(imageManager.tCover[0], recti(588 * mainGame->xScale, 48 * mainGame->yScale, 601 * mainGame->xScale, 68 * mainGame->yScale), rect<s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[1], recti(697 * mainGame->xScale, 48 * mainGame->yScale, 710 * mainGame->xScale, 68 * mainGame->yScale), rect<s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], recti(600 * mainGame->xScale, 51 * mainGame->yScale, 625 * mainGame->xScale, 70 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.card_count_color[0], 0xff000000, true, false, 0);
		DrawShadowText(numFont, dInfo.str_card_count[1], recti(710 * mainGame->xScale, 51 * mainGame->yScale, 735 * mainGame->xScale, 70 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), dInfo.card_count_color[1], 0xff000000, true, false, 0);
	}

	numFont->draw(dInfo.strLP[0], recti(305 * mainGame->xScale, 49 * mainGame->yScale, 614 * mainGame->xScale, 68 * mainGame->yScale), 0xff000000, true, false, 0);
	numFont->draw(dInfo.strLP[0], recti(305 * mainGame->xScale, 50 * mainGame->yScale, 616 * mainGame->xScale, 69 * mainGame->yScale), 0xffffff00, true, false, 0);
	numFont->draw(dInfo.strLP[1], recti(711 * mainGame->xScale, 49 * mainGame->yScale, 1010 * mainGame->xScale, 68 * mainGame->yScale), 0xff000000, true, false, 0);
	numFont->draw(dInfo.strLP[1], recti(711 * mainGame->xScale, 50 * mainGame->yScale, 1012 * mainGame->xScale, 69 * mainGame->yScale), 0xffffff00, true, false, 0);


	if(!dInfo.isTag || !dInfo.tag_player[0])
		textFont->draw(dInfo.hostname, recti(400 * mainGame->xScale, 18 * mainGame->yScale, 629 * mainGame->xScale, 37 * mainGame->yScale), 0xffffffff, false, false, 0);
	else
		textFont->draw(dInfo.hostname_tag, recti(400 * mainGame->xScale, 18 * mainGame->yScale, 629 * mainGame->xScale, 37 * mainGame->yScale), 0xffffffff, false, false, 0);
	if(!dInfo.isTag || !dInfo.tag_player[1]) {
		auto cld = textFont->getDimension(dInfo.clientname);
		textFont->draw(dInfo.clientname, recti(920 * mainGame->xScale - cld.Width, 18 * mainGame->yScale, 986 * mainGame->xScale, 37 * mainGame->yScale), 0xffffffff, false, false, 0);
	} else {
		auto cld = textFont->getDimension(dInfo.clientname_tag);
		textFont->draw(dInfo.clientname_tag, recti(920 * mainGame->xScale - cld.Width, 18 * mainGame->yScale, 986 * mainGame->xScale, 37 * mainGame->yScale), 0xffffffff, false, false, 0);
	}
	driver->draw2DRectangle(recti(632 * mainGame->xScale, 10 * mainGame->yScale, 688 * mainGame->xScale, 30 * mainGame->yScale), 0x00000000, 0x00000000, 0xffffffff, 0xffffffff);
	driver->draw2DRectangle(recti(632 * mainGame->xScale, 30 * mainGame->yScale, 688 * mainGame->xScale, 50 * mainGame->yScale), 0xffffffff, 0xffffffff, 0x00000000, 0x00000000);
	lpcFont->draw(dataManager.GetNumString(dInfo.turn), recti(635 * mainGame->xScale, 5 * mainGame->yScale, 685 * mainGame->xScale, 40 * mainGame->yScale), 0x80000000, true, false, 0);
	lpcFont->draw(dataManager.GetNumString(dInfo.turn), recti(635 * mainGame->xScale, 5 * mainGame->yScale, 687 * mainGame->xScale, 40 * mainGame->yScale), 0x8000ffff, true, false, 0);
    ClientCard* pcard;
	for(int i = 0; i < 5; ++i) {
		pcard = dField.mzone[0][i];
		if(pcard && pcard->code != 0)
			DrawStatus(pcard, (493 + i * 85) * xScale, 416 * yScale, (473 + i * 80) * xScale, 356 * yScale);
	}
	pcard = dField.mzone[0][5];
	if(pcard && pcard->code != 0)
		DrawStatus(pcard, 589 * xScale, 338 * yScale, 563 * xScale, 291 * yScale);
	pcard = dField.mzone[0][6];
	if(pcard && pcard->code != 0)
		DrawStatus(pcard, 743 * xScale, 338 * yScale, 712 * xScale, 291 * yScale);
	for(int i = 0; i < 5; ++i) {
		pcard = dField.mzone[1][i];
		if(pcard && (pcard->position & POS_FACEUP))
			DrawStatus(pcard, (803 - i * 68) * xScale, 235 * yScale, (779 - i * 71) * xScale, 272 * yScale);
	}
	pcard = dField.mzone[1][5];
	if(pcard && (pcard->position & POS_FACEUP))
		DrawStatus(pcard, 739 * xScale, 291 * yScale, 710 * xScale, 338 * yScale);
	pcard = dField.mzone[1][6];
	if(pcard && (pcard->position & POS_FACEUP))
		DrawStatus(pcard, 593 * xScale, 291 * yScale, 555 * xScale, 338 * yScale);
	if(dInfo.duel_rule < 4) {
	pcard = dField.szone[0][6];
	if(pcard) {
		adFont->draw(pcard->lscstring, recti(426 * mainGame->xScale, 394 * mainGame->yScale, 438 * mainGame->xScale, 414 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->lscstring, recti(427 * mainGame->xScale, 395 * mainGame->yScale, 439 * mainGame->xScale, 415 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[0][7];
	if(pcard) {
		adFont->draw(pcard->rscstring, recti(880 * mainGame->xScale, 394 * mainGame->yScale, 912 * mainGame->xScale, 414 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->rscstring, recti(881 * mainGame->xScale, 395 * mainGame->yScale, 913 * mainGame->xScale, 415 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[1][6];
	if(pcard) {
		adFont->draw(pcard->lscstring, recti(839 * mainGame->xScale, 245 * mainGame->yScale, 871 * mainGame->xScale, 265 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->lscstring, recti(840 * mainGame->xScale, 246 * mainGame->yScale, 872 * mainGame->xScale, 266 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[1][7];
	if(pcard) {
		adFont->draw(pcard->rscstring, recti(463 * mainGame->xScale, 245 * mainGame->yScale, 495 * mainGame->xScale, 265 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->rscstring, recti(464 * mainGame->xScale, 246 * mainGame->yScale, 496 * mainGame->xScale, 266 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	} else {
	pcard = dField.szone[0][0];
	if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
		adFont->draw(pcard->lscstring, recti(454 * mainGame->xScale, 430 * mainGame->yScale, 466 * mainGame->xScale, 450 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->lscstring, recti(455 * mainGame->xScale, 431 * mainGame->yScale, 467 * mainGame->xScale, 451 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[0][4];
	if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
		adFont->draw(pcard->rscstring, recti(850 * mainGame->xScale, 430 * mainGame->yScale, 882 * mainGame->xScale, 450 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->rscstring, recti(851 * mainGame->xScale, 431 * mainGame->yScale, 883 * mainGame->xScale, 451 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[1][0];
	if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
		adFont->draw(pcard->lscstring, recti(806 * mainGame->xScale, 222 * mainGame->yScale, 838 * mainGame->xScale, 242 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->lscstring, recti(807 * mainGame->xScale, 223 * mainGame->yScale, 839 * mainGame->xScale, 243 * mainGame->yScale), 0xffffffff, true, false, 0);
	}
	pcard = dField.szone[1][4];
	if(pcard && (pcard->type & TYPE_PENDULUM) && !pcard->equipTarget) {
		adFont->draw(pcard->rscstring, recti(498 * mainGame->xScale, 222 * mainGame->yScale, 530 * mainGame->xScale, 242 * mainGame->yScale), 0xff000000, true, false, 0);
		adFont->draw(pcard->rscstring, recti(499 * mainGame->xScale, 223 * mainGame->yScale, 531 * mainGame->xScale, 243 * mainGame->yScale), 0xffffffff, true, false, 0);
		}
	}
	if(dField.extra[0].size()) {
		int offset = (dField.extra[0].size() >= 10) ? 0 : mainGame->textFont->getDimension(dataManager.GetNumString(1)).Width;
		numFont->draw(dataManager.GetNumString(dField.extra[0].size()), recti((320 + offset)* mainGame->xScale, 562 * mainGame->yScale, 371 * mainGame->xScale, 552 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra[0].size()), recti((320 + offset)* mainGame->xScale, 563 * mainGame->yScale, 373 * mainGame->xScale, 553 * mainGame->yScale), 0xffffff00, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra_p_count[0], true), recti(340 * mainGame->xScale, 562 * mainGame->yScale, 391 * mainGame->xScale, 552 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra_p_count[0], true), recti(340 * mainGame->xScale, 563 * mainGame->yScale, 393 * mainGame->xScale, 553 * mainGame->yScale), 0xffffff00, true, false, 0);
	}
	if(dField.deck[0].size()) {
		numFont->draw(dataManager.GetNumString(dField.deck[0].size()), recti(907 * mainGame->xScale, 562 * mainGame->yScale, 1021 * mainGame->xScale, 552 * mainGame->yScale) , 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.deck[0].size()), recti(908 * mainGame->xScale, 563 * mainGame->yScale, 1023 * mainGame->xScale, 553 * mainGame->yScale), 0xffffff00, true, false, 0);
	}
	if (rule == 0) {
		if (dField.grave[0].size()) {
		numFont->draw(dataManager.GetNumString(dField.grave[0].size()), recti(837 * mainGame->xScale, 375 * mainGame->yScale, 984 * mainGame->xScale, 380 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.grave[0].size()), recti(837 * mainGame->xScale, 376 * mainGame->yScale, 986 * mainGame->xScale, 381 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
		if (dField.remove[0].size()) {
		numFont->draw(dataManager.GetNumString(dField.remove[0].size()), recti(1015 * mainGame->xScale, 375 * mainGame->yScale, 957 * mainGame->xScale, 380 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.remove[0].size()), recti(1015 * mainGame->xScale, 376 * mainGame->yScale, 959 * mainGame->xScale, 381 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
	} else {
		if (dField.grave[0].size()) {
			numFont->draw(dataManager.GetNumString(dField.grave[0].size()), recti(870 * mainGame->xScale, 456 * mainGame->yScale, 1002 * mainGame->xScale, 461 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.grave[0].size()), recti(870 * mainGame->xScale, 457 * mainGame->yScale, 1004 * mainGame->xScale, 462 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
		if (dField.remove[0].size()) {
			numFont->draw(dataManager.GetNumString(dField.remove[0].size()), recti(837 * mainGame->xScale, 375 * mainGame->yScale, 984 * mainGame->xScale, 380 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.remove[0].size()), recti(837 * mainGame->xScale, 376 * mainGame->yScale, 986 * mainGame->xScale, 381 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
	}
	if(dField.extra[1].size()) {
		int offset = (dField.extra[1].size() >= 10) ? 0 : mainGame->textFont->getDimension(dataManager.GetNumString(1)).Width;
		numFont->draw(dataManager.GetNumString(dField.extra[1].size()), recti((808 + offset) * mainGame->xScale, 207 * mainGame->yScale, 898 * mainGame->xScale, 232 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra[1].size()), recti((808 + offset) * mainGame->xScale, 208 * mainGame->yScale, 900 * mainGame->xScale, 233 * mainGame->yScale), 0xffffff00, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra_p_count[1], true), recti(828 * mainGame->xScale, 207 * mainGame->yScale, 918 * mainGame->xScale, 232 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.extra_p_count[1], true), recti(828 * mainGame->xScale, 208 * mainGame->yScale, 920 * mainGame->xScale, 233 * mainGame->yScale), 0xffffff00, true, false, 0);
	}
	if(dField.deck[1].size()) {
		numFont->draw(dataManager.GetNumString(dField.deck[1].size()), recti(465 * mainGame->xScale, 207 * mainGame->yScale, 481 * mainGame->xScale, 232 * mainGame->yScale), 0xff000000, true, false, 0);
		numFont->draw(dataManager.GetNumString(dField.deck[1].size()), recti(465 * mainGame->xScale, 208 * mainGame->yScale, 483 * mainGame->xScale, 233 * mainGame->yScale), 0xffffff00, true, false, 0);
	}
	if (rule == 0) {
		if (dField.grave[1].size()) {
			numFont->draw(dataManager.GetNumString(dField.grave[1].size()), recti(420 * mainGame->xScale, 310 * mainGame->yScale, 462 * mainGame->xScale, 281 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.grave[1].size()), recti(420 * mainGame->xScale, 311 * mainGame->yScale, 464 * mainGame->xScale, 282 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
		if (dField.remove[1].size()) {
			numFont->draw(dataManager.GetNumString(dField.remove[1].size()), recti(300 * mainGame->xScale, 310 * mainGame->yScale, 443 * mainGame->xScale, 340 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.remove[1].size()), recti(300 * mainGame->xScale, 311 * mainGame->yScale, 445 * mainGame->xScale, 341 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
	} else {
		if (dField.grave[1].size()) {
			numFont->draw(dataManager.GetNumString(dField.grave[1].size()), recti(455 * mainGame->xScale, 249 * mainGame->yScale, 462 * mainGame->xScale, 299 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.grave[1].size()), recti(455 * mainGame->xScale, 250 * mainGame->yScale, 464 * mainGame->xScale, 300 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
		if (dField.remove[1].size()) {
			numFont->draw(dataManager.GetNumString(dField.remove[1].size()), recti(420 * mainGame->xScale, 310 * mainGame->yScale, 462 * mainGame->xScale, 281 * mainGame->yScale), 0xff000000, true, false, 0);
			numFont->draw(dataManager.GetNumString(dField.remove[1].size()), recti(420 * mainGame->xScale, 311 * mainGame->yScale, 464 * mainGame->xScale, 282 * mainGame->yScale), 0xffffff00, true, false, 0);
		}
	}
}
void Game::DrawStatus(ClientCard* pcard, int x1, int y1, int x2, int y2) {
	adFont->draw(L"/", recti(x1 - 4, y1, x1 + 4, y1 + 20), 0xff000000, true, false, 0);
	adFont->draw(L"/", recti(x1 - 3, y1 + 1, x1 + 5, y1 + 21), 0xffffffff, true, false, 0);
	int w = adFont->getDimension(pcard->atkstring).Width;
	adFont->draw(pcard->atkstring, recti(x1 - 5 - w, y1, x1 - 5, y1 + 20), 0xff000000, false, false, 0);
	adFont->draw(pcard->atkstring, recti(x1 - 4 - w, y1 + 1, x1 - 4, y1 + 21),
		pcard->attack > pcard->base_attack ? 0xffffff00 : pcard->attack < pcard->base_attack ? 0xffff2090 : 0xffffffff, false, false, 0);
	if(pcard->type & TYPE_LINK) {
		w = adFont->getDimension(pcard->linkstring).Width;
		adFont->draw(pcard->linkstring, recti(x1 + 4, y1, x1 + 4 + w, y1 + 20), 0xff000000, false, false, 0);
		adFont->draw(pcard->linkstring, recti(x1 + 5, y1 + 1, x1 + 5 + w, y1 + 21), 0xff99ffff, false, false, 0);
	} else {
		w = adFont->getDimension(pcard->defstring).Width;
		adFont->draw(pcard->defstring, recti(x1 + 4, y1, x1 + 4 + w, y1 + 20), 0xff000000, false, false, 0);
		adFont->draw(pcard->defstring, recti(x1 + 5, y1 + 1, x1 + 5 + w, y1 + 21),
			pcard->defense > pcard->base_defense ? 0xffffff00 : pcard->defense < pcard->base_defense ? 0xffff2090 : 0xffffffff, false, false, 0);
		adFont->draw(pcard->lvstring, recti(x2, y2, x2 + 2, y2 + 20), 0xff000000, false, false, 0);
		adFont->draw(pcard->lvstring, recti(x2 + 1, y2, x2 + 3, y2 + 21),
			(pcard->type & TYPE_XYZ) ? 0xffff80ff : (pcard->type & TYPE_TUNER) ? 0xffffff00 : 0xffffffff, false, false, 0);
	}
}
void Game::DrawGUI() {
	if(imageLoading.size()) {
		for(auto mit = imageLoading.begin(); mit != imageLoading.end(); ++mit)
			mit->first->setImage(imageManager.GetTexture(mit->second));
		imageLoading.clear();
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
    	dimension2d<u32> orisize = showimg->getOriginalSize();
		switch(showcard) {
		case 1: {
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti(574 * mainGame->xScale, 150 * mainGame->yScale, (574 + CARD_IMG_WIDTH) * mainGame->xScale, (150 + CARD_IMG_HEIGHT) * mainGame->yScale), recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tMask, recti(574 * mainGame->xScale, 150 * mainGame->yScale, (574 + (showcarddif > CARD_IMG_WIDTH ? CARD_IMG_WIDTH : showcarddif)) * mainGame->xScale, 404 * mainGame->yScale),
			                    recti(CARD_IMG_HEIGHT - showcarddif, 0, CARD_IMG_HEIGHT - (showcarddif > CARD_IMG_WIDTH ? showcarddif - CARD_IMG_WIDTH : 0), CARD_IMG_HEIGHT), 0, 0, true);
			showcarddif += 15;
			if(showcarddif >= CARD_IMG_HEIGHT) {
				showcard = 2;
				showcarddif = 0;
			}
			break;
		}
		case 2: {
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti(574 * mainGame->xScale, 150 * mainGame->yScale, (574 + CARD_IMG_WIDTH) * mainGame->xScale, (150 + CARD_IMG_HEIGHT) * mainGame->yScale), recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tMask, recti((574 + showcarddif) * mainGame->xScale, 150 * mainGame->yScale, 751 * mainGame->xScale, 404 * mainGame->yScale), recti(0, 0, (CARD_IMG_WIDTH - showcarddif), CARD_IMG_HEIGHT), 0, 0, true);
			showcarddif += 15;
			if(showcarddif >= CARD_IMG_WIDTH) {
				showcard = 0;
			}
			break;
		}
		case 3: {
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti(574 * mainGame->xScale, 150 * mainGame->yScale, (574 + CARD_IMG_WIDTH) * mainGame->xScale, (150 + CARD_IMG_HEIGHT) * mainGame->yScale), recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tNegated, recti((536 + showcarddif) * mainGame->xScale, (141 + showcarddif) * mainGame->yScale, (793 - showcarddif) * mainGame->xScale, (397 - showcarddif) * mainGame->yScale), recti(0, 0, 128, 128), 0, 0, true);
			if(showcarddif < 64)
				showcarddif += 4;
			break;
		}
		case 4: {
			matManager.c2d[0] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[1] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[2] = (showcarddif << 24) | 0xffffff;
			matManager.c2d[3] = (showcarddif << 24) | 0xffffff;
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti(574 * mainGame->xScale, 154 * mainGame->yScale, 751 * mainGame->xScale, 404 * mainGame->yScale),
			                    recti(0, 0, orisize.Width, orisize.Height), 0, matManager.c2d, true);
			if(showcarddif < 255)
				showcarddif += 17;
			break;
		}
		case 5: {
			matManager.c2d[0] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[1] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[2] = (showcarddif << 25) | 0xffffff;
			matManager.c2d[3] = (showcarddif << 25) | 0xffffff;
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti((662 - showcarddif * 0.69685f) * mainGame->xScale, (277 - showcarddif) * mainGame->yScale, (662 + showcarddif * 0.69685f) * mainGame->xScale, (277 + showcarddif) * mainGame->yScale),
			                    recti(0, 0, orisize.Width, orisize.Height), 0, matManager.c2d, true);
			if(showcarddif < 127)
				showcarddif += 9;
			break;
		}
		case 6: {
			driver->draw2DImage(imageManager.GetTexture(showcardcode), recti(574 * mainGame->xScale, 150 * mainGame->yScale, (574 + CARD_IMG_WIDTH) * mainGame->xScale, (150 + CARD_IMG_HEIGHT) * mainGame->yScale), recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
			driver->draw2DImage(imageManager.tNumber, recti((536 + showcarddif) * mainGame->xScale, (141 + showcarddif) * mainGame->yScale, (793 - showcarddif) * mainGame->xScale, (397 - showcarddif) * mainGame->yScale),
			                    recti((showcardp % 5) * 64, (showcardp / 5) * 64, (showcardp % 5 + 1) * 64, (showcardp / 5 + 1) * 64), 0, 0, true);
			if(showcarddif < 64)
				showcarddif += 4;
			break;
		}
		case 7: {
			core::position2d<s32> corner[4];
			float y = sin(showcarddif * 3.1415926f / 180.0f) * CARD_IMG_HEIGHT * mainGame->yScale;
			corner[0] = core::position2d<s32>(574 * mainGame->xScale - (CARD_IMG_HEIGHT * mainGame->yScale - y) * 0.3f , 404 * mainGame->yScale - y);
			corner[1] = core::position2d<s32>(751 * mainGame->xScale + (CARD_IMG_HEIGHT * mainGame->yScale - y) * 0.3f , 404 * mainGame->yScale - y);
			corner[2] = core::position2d<s32>(574 * mainGame->xScale, 404 * mainGame->yScale);
			corner[3] = core::position2d<s32>(751 * mainGame->xScale, 404 * mainGame->yScale);
			irr::gui::Draw2DImageQuad(driver, imageManager.GetTexture(showcardcode), rect<s32>(0, 0, orisize.Width, orisize.Height), corner);
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
		case 100: {
			if(showcardp < 60) {
				driver->draw2DImage(imageManager.tHand[(showcardcode >> 16) & 0x3],
						recti(615 * mainGame->xScale, showcarddif * mainGame->yScale, (615 + 89) * mainGame->xScale, (128 + showcarddif) * mainGame->yScale),
						recti(0, 0, 89, 128), 0, 0, true);
				driver->draw2DImage(imageManager.tHand[showcardcode & 0x3],
										recti(615 * mainGame->xScale, (540 - showcarddif) * mainGame->yScale, (615 + 89) * mainGame->xScale, (128 + 540 - showcarddif) * mainGame->yScale),
										recti(0, 0, 89, 128), 0, 0, true);
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
				DrawShadowText(lpcFont, lstr, recti(550 * mainGame->xScale - pos.Width / 2 - (9 - showcardp) * 40 * mainGame->xScale, 270 * mainGame->yScale, 850 * mainGame->xScale, 350 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), alpha | 0xffffff);
			} else if(showcardp < showcarddif) {
				DrawShadowText(lpcFont, lstr, recti(550 * mainGame->xScale - pos.Width / 2, 270 * mainGame->yScale, 850 * mainGame->xScale, 350 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
				if(dInfo.vic_string && (showcardcode == 1 || showcardcode == 2)) {
					driver->draw2DRectangle(0xa0000000, recti(540 * mainGame->xScale, 320 * mainGame->yScale, 800 * mainGame->xScale, 340 * mainGame->yScale));
					DrawShadowText(guiFont, dInfo.vic_string, recti(500 * mainGame->xScale, 320 * mainGame->yScale, 840 * mainGame->xScale, 340 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, true);
				}
			} else if(showcardp < showcarddif + 10) {
				int alpha = ((showcarddif + 10 - showcardp) * 25) << 24;
				DrawShadowText(lpcFont, lstr, recti(550 * mainGame->xScale - pos.Width / 2 + (showcardp - showcarddif) * 40 * mainGame->xScale, 270 * mainGame->yScale, 850 * mainGame->xScale, 350 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), alpha | 0xffffff);
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
		driver->drawVertexPrimitiveList(&matManager.vArrow[attack_sv], 12, matManager.iArrow, 10, EVT_STANDARD, EPT_TRIANGLE_STRIP);
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
	for(int i = 0; i < 8; ++i) {
		static unsigned int chatColor[] = {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff, 0xff8080ff, 0xffff4040, 0xffff4040,
		                                   0xffff4040, 0xff40ff40, 0xff4040ff, 0xff40ffff, 0xffff40ff, 0xffffff40, 0xffffffff, 0xff808080, 0xff404040};
		if(chatTiming[i]) {
			chatTiming[i]--;
			if(mainGame->dInfo.isStarted && i >= 5)
				continue;
			if(!showChat && i > 2)
				continue;
			int w = textFont->getDimension(chatMsg[i].c_str()).Width;
			driver->draw2DRectangle(recti(305 * mainGame->xScale, (596 - 20 * i) * mainGame->yScale, (307 + w) * mainGame->xScale, (616 - 20 * i) * mainGame->yScale), 0xa0000000, 0xa0000000, 0xa0000000, 0xa0000000);
			textFont->draw(chatMsg[i].c_str(), rect<s32>(305 * mainGame->xScale, (595 - 20 * i) * mainGame->yScale, 1020 * mainGame->xScale, (615 - 20 * i) * mainGame->yScale), 0xff000000, false, false);
			textFont->draw(chatMsg[i].c_str(), rect<s32>(306 * mainGame->xScale, (596 - 20 * i) * mainGame->yScale, 1021 * mainGame->xScale, (616 - 20 * i) * mainGame->yScale), chatColor[chatType[i]], false, false);
		}
	}
}
void Game::DrawBackImage(irr::video::ITexture* texture) {
	if(!texture)
		return;
	driver->draw2DImage(texture, recti(0, 0, GAME_WIDTH * mainGame->xScale, GAME_HEIGHT * mainGame->yScale), recti(0, 0, texture->getOriginalSize().Width, texture->getOriginalSize().Height));
}
void Game::ShowElement(irr::gui::IGUIElement * win, int autoframe) {
	FadingUnit fu;
	fu.fadingSize = win->getRelativePosition();
	for(auto fit = fadingList.begin(); fit != fadingList.end(); ++fit)
		if(win == fit->guiFading && win != wOptions && win != wANNumber) // the size of wOptions is always setted by ClientField::ShowSelectOption before showing it
			fu.fadingSize = fit->fadingSize;
	irr::core::position2di center = fu.fadingSize.getCenter();
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
	fadingList.push_back(fu);
}
void Game::HideElement(irr::gui::IGUIElement * win, bool set_action) {
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
void Game::DrawThumb(code_pointer cp, position2di pos, std::unordered_map<int, int>* lflist) {
	const int width = 44; //standard pic size, maybe it should be defined in game.h
	const int height = 64;
	int code = cp->first;
	int lcode = cp->second.alias;
	if(lcode == 0)
		lcode = code;
	irr::video::ITexture* img = imageManager.GetTexture(code);
	if(img == NULL)
		return; //NULL->getSize() will cause a crash
	dimension2d<u32> size = img->getOriginalSize();
	driver->draw2DImage(img, rect<s32>(pos.X, pos.Y, pos.X + width * mainGame->xScale, pos.Y + height * mainGame->yScale), rect<s32>(0, 0, size.Width, size.Height));

	if(lflist->count(lcode)) {
		switch((*lflist)[lcode]) {
		case 0:
			driver->draw2DImage(imageManager.tLim, recti(pos.X, pos.Y, pos.X + 20 * mainGame->xScale, pos.Y + 20 * mainGame->yScale), recti(0, 0, 64, 64), 0, 0, true);
			break;
		case 1:
			driver->draw2DImage(imageManager.tLim, recti(pos.X, pos.Y, pos.X + 20 * mainGame->xScale, pos.Y + 20 * mainGame->yScale), recti(64, 0, 128, 64), 0, 0, true);
			break;
		case 2:
			driver->draw2DImage(imageManager.tLim, recti(pos.X, pos.Y, pos.X + 20 * mainGame->xScale, pos.Y + 20 * mainGame->yScale), recti(0, 64, 64, 128), 0, 0, true);
			break;
		}
	}
	if(cbLimit->getSelected() >= 4 && (cp->second.ot & gameConf.defaultOT)) {
		switch(cp->second.ot) {
		case 1:
			driver->draw2DImage(imageManager.tOT, recti(pos.X + 0 * mainGame->xScale, pos.Y + 45 * mainGame->yScale, pos.X + 40 * mainGame->xScale, pos.Y + 65 * mainGame->yScale), recti(0, 128, 128, 192), 0, 0, true);
			break;
		case 2:
			driver->draw2DImage(imageManager.tOT, recti(pos.X + 0 * mainGame->xScale, pos.Y + 45 * mainGame->yScale, pos.X + 40 * mainGame->xScale, pos.Y + 65 * mainGame->yScale), recti(0, 192, 128, 256), 0, 0, true);
			break;
		}
	} else if(cbLimit->getSelected() >= 4 || !(cp->second.ot & gameConf.defaultOT)) {
		switch(cp->second.ot) {
		case 1:
			driver->draw2DImage(imageManager.tOT, recti(pos.X + 0 * mainGame->xScale, pos.Y + 45 * mainGame->yScale, pos.X + 40 * mainGame->xScale, pos.Y + 65 * mainGame->yScale), recti(0, 0, 128, 64), 0, 0, true);
			break;
		case 2:
			driver->draw2DImage(imageManager.tOT, recti(pos.X + 0 * mainGame->xScale, pos.Y + 45 * mainGame->yScale, pos.X + 40 * mainGame->xScale, pos.Y + 65 * mainGame->yScale), recti(0, 64, 128, 128), 0, 0, true);
			break;
		}
	}
}
void Game::DrawDeckBd() {
	wchar_t textBuffer[64];
	//main deck
	driver->draw2DRectangle(recti(310 * mainGame->xScale, 137 * mainGame->yScale, 410 * mainGame->xScale, 157 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 136 * mainGame->yScale, 410 * mainGame->xScale, 157 * mainGame->yScale));
	DrawShadowText(textFont, dataManager.GetSysString(1330), recti(300 * mainGame->xScale, 136 * mainGame->yScale, 395 * mainGame->xScale, 156 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.current_deck.main.size()], recti(360 * mainGame->xScale, 137 * mainGame->yScale, 420 * mainGame->xScale, 157 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
    driver->draw2DRectangle(recti(310 * mainGame->xScale, 160 * mainGame->yScale, 797 * mainGame->xScale, 436 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 159 * mainGame->yScale, 797 * mainGame->xScale, 436 * mainGame->yScale));
	//type count 2DRectangle
	driver->draw2DRectangle(recti(638 * mainGame->xScale, 137 * mainGame->yScale, 797 * mainGame->xScale, 157 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(637 * mainGame->xScale, 136 * mainGame->yScale, 797 * mainGame->xScale, 157 * mainGame->yScale));
	//monster count
	driver->draw2DImage(imageManager.tCardType, recti(645 * mainGame->xScale, 136 * mainGame->yScale, (645+14+3/8) * mainGame->xScale, 156 * mainGame->yScale), recti(0, 0, 23, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.main, TYPE_MONSTER)], recti(670 * mainGame->xScale, 137 * mainGame->yScale, 690 * mainGame->xScale, 157 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	//spell count
	driver->draw2DImage(imageManager.tCardType, recti(695 * mainGame->xScale, 136 * mainGame->yScale, (695+14+3/8) * mainGame->xScale, 156 * mainGame->yScale), recti(23, 0, 46, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.main, TYPE_SPELL)], recti(720 * mainGame->xScale, 138 * mainGame->yScale, 740 * mainGame->xScale, 158 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
    //trap count
	driver->draw2DImage(imageManager.tCardType, recti(745 * mainGame->xScale, 136 * mainGame->yScale, (745+14+3/8) * mainGame->xScale, 156 * mainGame->yScale), recti(46, 0, 69, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.main, TYPE_TRAP)], recti(770 * mainGame->xScale, 137 * mainGame->yScale, 790 * mainGame->xScale, 157 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
    int lx;
	float dx;
	if(deckManager.current_deck.main.size() <= 40) {
		dx = 436.0f / 9;
		lx = 10;
	} else {
		lx = (deckManager.current_deck.main.size() - 41) / 4 + 11;
		dx = 436.0f / (lx - 1);
	}
	for(size_t i = 0; i < deckManager.current_deck.main.size(); ++i) {
		DrawThumb(deckManager.current_deck.main[i], position2di((314 + (i % lx) * dx)  * mainGame->xScale, (164 + (i / lx) * 68)  * mainGame->yScale), deckBuilder.filterList);
		if(deckBuilder.hovered_pos == 1 && deckBuilder.hovered_seq == (int)i)
			driver->draw2DRectangleOutline(recti((313 + (i % lx) * dx)  * mainGame->xScale, (163 + (i / lx) * 68) * mainGame->yScale, (359 + (i % lx) * dx) * mainGame->xScale, (228 + (i / lx) * 68) * mainGame->yScale));
	}
	//extra deck
	driver->draw2DRectangle(recti(310 * mainGame->xScale, 440 * mainGame->yScale, 410 * mainGame->xScale, 460 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 439 * mainGame->yScale, 410 * mainGame->xScale, 460 * mainGame->yScale));
	DrawShadowText(textFont, dataManager.GetSysString(1331), recti(300 * mainGame->xScale, 439 * mainGame->yScale, 395 * mainGame->xScale, 459 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.current_deck.extra.size()], recti(360 * mainGame->xScale, 440 * mainGame->yScale, 420 * mainGame->xScale, 460 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	driver->draw2DRectangle(recti(310 * mainGame->xScale, 463 * mainGame->yScale, 797 * mainGame->xScale, 533 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 462 * mainGame->yScale, 797 * mainGame->xScale, 533 * mainGame->yScale));
	//type count 2DRectangle
	driver->draw2DRectangle(recti(582 * mainGame->xScale, 440 * mainGame->yScale, 797 * mainGame->xScale, 460 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(581 * mainGame->xScale, 439 * mainGame->yScale, 797 * mainGame->xScale, 460 * mainGame->yScale));
	//fusion count
	driver->draw2DImage(imageManager.tCardType, recti(595 * mainGame->xScale, 440 * mainGame->yScale, (595+14+3/8) * mainGame->xScale, 460 * mainGame->yScale), recti(0, 32, 23, 64), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.extra, TYPE_FUSION)], recti(620 * mainGame->xScale, 440 * mainGame->yScale, 640 * mainGame->xScale, 460 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	//synchro count
	driver->draw2DImage(imageManager.tCardType, recti(645 * mainGame->xScale, 440 * mainGame->yScale, (645+14+3/8) * mainGame->xScale, 460 * mainGame->yScale), recti(23, 32, 46, 64), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.extra, TYPE_SYNCHRO)], recti(670 * mainGame->xScale, 440 * mainGame->yScale, 690 * mainGame->xScale, 460 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	//XYZ count
	driver->draw2DImage(imageManager.tCardType, recti(695 * mainGame->xScale, 440 * mainGame->yScale, (695+14+3/8) * mainGame->xScale, 460 * mainGame->yScale), recti(46, 32, 69, 64), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.extra, TYPE_XYZ)], recti(720 * mainGame->xScale, 440 * mainGame->yScale, 740 * mainGame->xScale, 460 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	//link count
	driver->draw2DImage(imageManager.tCardType, recti(745 * mainGame->xScale, 440 * mainGame->yScale, (745+14+3/8) * mainGame->xScale, 460 * mainGame->yScale), recti(0, 64, 23, 96), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.extra, TYPE_LINK)], recti(770 * mainGame->xScale, 440 * mainGame->yScale, 790 * mainGame->xScale, 460 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	if(deckManager.current_deck.extra.size() <= 10)
		dx = 436.0f / 9;
	else dx = 436.0f / (deckManager.current_deck.extra.size() - 1);
	for(size_t i = 0; i < deckManager.current_deck.extra.size(); ++i) {
		DrawThumb(deckManager.current_deck.extra[i], position2di((314 + i * dx) * mainGame->xScale, 466 * mainGame->yScale), deckBuilder.filterList);
		if(deckBuilder.hovered_pos == 2 && deckBuilder.hovered_seq == (int)i)
			driver->draw2DRectangleOutline(recti((313 + i * dx) * mainGame->xScale, 465 * mainGame->yScale, (359 + i * dx) * mainGame->xScale, 531 * mainGame->yScale));
	}
	//side deck
	driver->draw2DRectangle(recti(310 * mainGame->xScale, 537 * mainGame->yScale, 410 * mainGame->xScale, 557 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 536 * mainGame->yScale, 410 * mainGame->xScale, 557 * mainGame->yScale));
	DrawShadowText(textFont, dataManager.GetSysString(1332), recti(300 * mainGame->xScale, 536 * mainGame->yScale, 395 * mainGame->xScale, 556 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.current_deck.side.size()], recti(360 * mainGame->xScale, 537 * mainGame->yScale, 420 * mainGame->xScale, 557 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	driver->draw2DRectangle(recti(310 * mainGame->xScale, 560 * mainGame->yScale, 797 * mainGame->xScale, 630 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(309 * mainGame->xScale, 559 * mainGame->yScale, 797 * mainGame->xScale, 630 * mainGame->yScale));
	//type count 2DRectangle
	driver->draw2DRectangle(recti(638 * mainGame->xScale, 537 * mainGame->yScale, 797 * mainGame->xScale, 557 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(637 * mainGame->xScale, 536 * mainGame->yScale, 797 * mainGame->xScale, 557 * mainGame->yScale));
	//monster count
	driver->draw2DImage(imageManager.tCardType, recti(645 * mainGame->xScale, 537 * mainGame->yScale, (645+14+3/8) * mainGame->xScale, 557 * mainGame->yScale), recti(0, 0, 23, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.side, TYPE_MONSTER)], recti(670 * mainGame->xScale, 537 * mainGame->yScale, 690 * mainGame->xScale, 557 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	//spell count
	driver->draw2DImage(imageManager.tCardType, recti(695 * mainGame->xScale, 537 * mainGame->yScale, (695+14+3/8) * mainGame->xScale, 557 * mainGame->yScale), recti(23, 0, 46, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.side, TYPE_SPELL)], recti(720 * mainGame->xScale, 537 * mainGame->yScale, 740 * mainGame->xScale, 557 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
    //trap count
	driver->draw2DImage(imageManager.tCardType, recti(745 * mainGame->xScale, 537 * mainGame->yScale, (745+14+3/8) * mainGame->xScale, 557 * mainGame->yScale), recti(46, 0, 69, 32), 0, 0, true);
	DrawShadowText(numFont, dataManager.numStrings[deckManager.TypeCount(deckManager.current_deck.side, TYPE_TRAP)], recti(770 * mainGame->xScale, 537 * mainGame->yScale, 790 * mainGame->xScale, 557 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	if(deckManager.current_deck.side.size() <= 10)
		dx = 436.0f / 9;
	else dx = 436.0f / (deckManager.current_deck.side.size() - 1);
	for(size_t i = 0; i < deckManager.current_deck.side.size(); ++i) {
		DrawThumb(deckManager.current_deck.side[i], position2di((314 + i * dx) * mainGame->xScale, 564 * mainGame->yScale), deckBuilder.filterList);
		if(deckBuilder.hovered_pos == 3 && deckBuilder.hovered_seq == (int)i)
			driver->draw2DRectangleOutline(recti((313 + i * dx) * mainGame->xScale, 563 * mainGame->yScale, (359 + i * dx) * mainGame->xScale, 629 * mainGame->yScale));
	}
	//search result
	driver->draw2DRectangle(recti(805 * mainGame->xScale, 137 * mainGame->yScale, 915 * mainGame->xScale, 157 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(804 * mainGame->xScale, 136 * mainGame->yScale, 915 * mainGame->xScale, 157 * mainGame->yScale));
	DrawShadowText(textFont, dataManager.GetSysString(1333), recti(790 * mainGame->xScale, 136 * mainGame->yScale, 900 * mainGame->xScale, 156 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	DrawShadowText(numFont, deckBuilder.result_string, recti(865 * mainGame->xScale, 136 * mainGame->yScale, 925 * mainGame->xScale, 156 * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, true, false);
	driver->draw2DRectangle(recti(805 * mainGame->xScale, 160 * mainGame->yScale, 1020 * mainGame->xScale, 630 * mainGame->yScale), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(recti(804 * mainGame->xScale, 159 * mainGame->yScale, 1020 * mainGame->xScale, 630 * mainGame->yScale));
#ifdef _IRR_ANDROID_PLATFORM_
	for(size_t i = 0; i < 7 && i + scrFilter->getPos() < deckBuilder.results.size(); ++i) {
		code_pointer ptr = deckBuilder.results[i + scrFilter->getPos()];
		if(deckBuilder.hovered_pos == 4 && deckBuilder.hovered_seq == (int)i)
			driver->draw2DRectangle(0x80000000, recti(806 * mainGame->xScale, (164 + i * 66) * mainGame->yScale, 1019 * mainGame->xScale, (230 + i * 66) * mainGame->yScale));
		DrawThumb(ptr, position2di(805 * mainGame->xScale, (165 + i * 66) * mainGame->yScale), deckBuilder.filterList);
		if(ptr->second.type & TYPE_MONSTER) {
			myswprintf(textBuffer, L"%ls", dataManager.GetName(ptr->first));
			DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (164 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (185 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
			if(!(ptr->second.type & TYPE_LINK)) {
				const wchar_t* form = L"\u2605";
				if(ptr->second.type & TYPE_XYZ) form = L"\u2606";
				myswprintf(textBuffer, L"%ls/%ls %ls%d", dataManager.FormatAttribute(ptr->second.attribute), dataManager.FormatRace(ptr->second.race), form, ptr->second.level);
				DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (186 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (207 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
				if(ptr->second.attack < 0 && ptr->second.defense < 0)
					myswprintf(textBuffer, L"?/?");
				else if(ptr->second.attack < 0)
					myswprintf(textBuffer, L"?/%d", ptr->second.defense);
				else if(ptr->second.defense < 0)
					myswprintf(textBuffer, L"%d/?", ptr->second.attack);
				else myswprintf(textBuffer, L"%d/%d", ptr->second.attack, ptr->second.defense);
			} else {
				myswprintf(textBuffer, L"%ls/%ls LINK-%d", dataManager.FormatAttribute(ptr->second.attribute), dataManager.FormatRace(ptr->second.race), ptr->second.level);
				DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (186 + i * 66) * mainGame->yScale, 955 * mainGame->xScale, (207 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
				if(ptr->second.attack < 0)
					myswprintf(textBuffer, L"?/-");
				else myswprintf(textBuffer, L"%d/-", ptr->second.attack);
			}//*
			if(ptr->second.type & TYPE_PENDULUM) {
				wchar_t scaleBuffer[16];
				myswprintf(scaleBuffer, L" %d/%d", ptr->second.lscale, ptr->second.rscale);
				mywcscat(textBuffer, scaleBuffer);
			}
			if((ptr->second.ot & 0x3) == 1)
				mywcscat(textBuffer, L" [OCG]");
			else if((ptr->second.ot & 0x3) == 2)
				mywcscat(textBuffer, L" [TCG]");
			else if((ptr->second.ot & 0x7) == 4)
				mywcscat(textBuffer, L" [Custom]");
			DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (208 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (229 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
		} else {
			myswprintf(textBuffer, L"%ls", dataManager.GetName(ptr->first));
			DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (164 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (185 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
			const wchar_t* ptype = dataManager.FormatType(ptr->second.type);
			DrawShadowText(textFont, ptype, recti(850 * mainGame->xScale, (186 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (207 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
			textBuffer[0] = 0;
			if((ptr->second.ot & 0x3) == 1)
				mywcscat(textBuffer, L"[OCG]");
			else if((ptr->second.ot & 0x3) == 2)
				mywcscat(textBuffer, L"[TCG]");
			else if((ptr->second.ot & 0x7) == 4)
				mywcscat(textBuffer, L"[Custom]");
			DrawShadowText(textFont, textBuffer, recti(850 * mainGame->xScale, (208 + i * 66) * mainGame->yScale, 1000 * mainGame->xScale, (229 + i * 66) * mainGame->yScale), recti(0, 1 * mainGame->yScale, 2 * mainGame->xScale, 0), 0xffffffff, 0xff000000, false, false);
		}
	}
#endif
	if(deckBuilder.is_draging) {
		DrawThumb(deckBuilder.draging_pointer, position2di(deckBuilder.dragx - 22, deckBuilder.dragy - 32), deckBuilder.filterList);
	}
}
}
