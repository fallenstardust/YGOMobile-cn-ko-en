#include "game.h"
#include "client_card.h"
#include "materials.h"
#include "image_manager.h"
#include "deck_manager.h"
#include "duelclient.h"

namespace ygo {

/**
 * @brief 设置一个由四个顶点组成的矩形3D顶点数据
 *
 * 该函数用于快速设置一个矩形平面的四个顶点数据，通常用于创建二维界面元素或简单几何体。
 * 四个顶点按照左上、右上、左下、右下的顺序设置，形成两个三角形组成的矩形。
 * 所有顶点使用相同的法线和颜色属性。
 *
 * @param v 指向包含至少4个S3DVertex元素的数组指针，用于存储顶点数据
 * @param x1 矩形左边界X坐标
 * @param y1 矩形上边界Y坐标
 * @param x2 矩形右边界X坐标
 * @param y2 矩形下边界Y坐标
 * @param z 所有顶点的Z坐标（深度）
 * @param nz 法线向量的Z分量（法线方向为(0, 0, nz)）
 * @param tu1 左侧纹理U坐标
 * @param tv1 上侧纹理V坐标
 * @param tu2 右侧纹理U坐标
 * @param tv2 下侧纹理V坐标
 */
inline void SetS3DVertex(irr::video::S3DVertex* v, irr::f32 x1, irr::f32 y1, irr::f32 x2, irr::f32 y2, irr::f32 z, irr::f32 nz,irr::f32 tu1, irr::f32 tv1, irr::f32 tu2, irr::f32 tv2) {
	// 设置矩形的四个顶点：左上、右上、左下、右下
	v[0] = irr::video::S3DVertex(x1, y1, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu1, tv1);
	v[1] = irr::video::S3DVertex(x2, y1, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu2, tv1);
	v[2] = irr::video::S3DVertex(x1, y2, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu1, tv2);
	v[3] = irr::video::S3DVertex(x2, y2, z, 0, 0, nz, irr::video::SColor(255, 255, 255, 255), tu2, tv2);
}
/**
 * @brief 设置卡片的3D顶点坐标
 *
 * 该函数用于计算并设置游戏卡片各个面的3D顶点坐标，包括正面、背面、轮廓线等。
 * 根据屏幕的缩放比例计算默认缩放值，然后为卡片的不同部分设置相应的顶点坐标。
 *
 * @note 该函数无参数且无返回值
 */
void Game::SetCardS3DVertex() {
    // 计算卡片的默认缩放比例，基于x轴和y轴缩放差异
    irr::f32 defalutScale = (mainGame->xScale - mainGame->yScale) / 9.5f;
    ALOGD("cc drawing defalutScale = %f",defalutScale);

    // 设置卡片正面的顶点坐标
    SetS3DVertex(matManager.vCardFront, -0.35f + defalutScale, -0.5f, 0.35f - defalutScale, 0.5f, 0, 1, 0, 0, 1, 1);

    // 设置卡片外轮廓的顶点坐标
    SetS3DVertex(matManager.vCardOutline, -0.375f + defalutScale, -0.54f, 0.37f - defalutScale, 0.54f, 0, 1, 0, 0, 1, 1);

    // 设置卡片反向外轮廓的顶点坐标
    SetS3DVertex(matManager.vCardOutliner, 0.37f - defalutScale, -0.54f, -0.375f + defalutScale, 0.54f, 0, 1, 0, 0, 1, 1);

    // 设置卡片背面的顶点坐标
    SetS3DVertex(matManager.vCardBack, 0.35f - defalutScale, -0.5f, -0.35f + defalutScale, 0.5f, 0, -1, 0, 0, 1, 1);
}
/**
 * @brief 绘制选择框的线框效果
 *
 * 该函数用于绘制一个四边形的选择框线框，支持两种模式：普通模式和动态条纹模式。
 * 条纹模式下会根据linePatternD3D变量的值来实现动态移动的效果。
 *
 * @param vec 指向包含四个顶点的数组，表示要绘制的四边形的四个角点
 * @param strip 是否启用条纹动画效果。true表示启用，false表示禁用
 * @param width 线宽，实际使用的线宽为width+2
 * @param cv 未使用参数（可能为保留参数或历史遗留）
 */
void Game::DrawSelectionLine(irr::video::S3DVertex* vec, bool strip, int width, float* cv) {
		glLineWidth(width+2);
		driver->setMaterial(matManager.mOutLine);

        // 条纹动画模式：通过分段绘制线段模拟移动的条纹效果
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
            // 普通模式：直接连接四个顶点形成完整的矩形框
			driver->draw3DLine(vec[0].Pos, vec[1].Pos);
			driver->draw3DLine(vec[1].Pos, vec[3].Pos);
			driver->draw3DLine(vec[3].Pos, vec[2].Pos);
			driver->draw3DLine(vec[2].Pos, vec[0].Pos);
		}

}
/**
 * @brief 绘制指定GUI元素的选择框线（常用于高亮选中项）
 *
 * 此函数根据linePatternD3D的值动态绘制一个围绕指定GUI元素的边框。
 * 边框由四条短线段组成，并且会随着linePatternD3D的变化呈现出动画效果。
 *
 * @param element 指向要绘制选择线的GUI元素
 * @param width   线条宽度（影响绘制矩形的粗细）
 * @param color   线条颜色（使用irr::video::SColor类型表示）
 */
void Game::DrawSelectionLine(irr::gui::IGUIElement* element, int width, irr::video::SColor color) {
	// 获取元素在屏幕上的绝对位置
	irr::core::recti pos = element->getAbsolutePosition();
	float x1 = pos.UpperLeftCorner.X;
	float x2 = pos.LowerRightCorner.X;
	float y1 = pos.UpperLeftCorner.Y;
	float y2 = pos.LowerRightCorner.Y;
	float w = pos.getWidth();
	float h = pos.getHeight();

	// 根据linePatternD3D的值分两阶段绘制四条边的线段
	if(linePatternD3D < 15) {
		// 第一阶段：从0到14，逐步增长线段长度
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y1 - 1 - width, x1 + (w * (linePatternD3D + 1) / 15.0) + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, irr::core::recti(x2 - (w * (linePatternD3D + 1) / 15.0) - 1 - width, y2 + 1, x2 + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y1 - 1 - width, x1 - 1, y2 - (h * (linePatternD3D + 1) / 15.0) + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x2 + 1, y1 + (h * (linePatternD3D + 1) / 15.0) - 1 - width, x2 + 1 + width, y2 + 1 + width));
	} else {
		// 第二阶段：从15到29，逐步缩短剩余未绘制部分
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width + (w * (linePatternD3D - 14) / 15.0), y1 - 1 - width, x2 + 1 + width, y1 - 1));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y2 + 1, x2 - (w * (linePatternD3D - 14) / 15.0) + 1 + width, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x1 - 1 - width, y2 - (h * (linePatternD3D - 14) / 15.0) - 1 - width, x1 - 1, y2 + 1 + width));
		driver->draw2DRectangle(color, irr::core::recti(x2 + 1, y1 - 1 - width, x2 + 1 + width, y1 + (h * (linePatternD3D - 14) / 15.0) + 1 + width));
	}
}

/**
 * @brief 绘制游戏背景及场地区域相关元素。
 *
 * 此函数负责绘制整个决斗界面的背景、场地魔法卡区域、选择提示框、总攻击力显示以及禁用区域等视觉元素。
 * 包括根据当前规则判断是否绘制场地魔法卡，并处理玩家可选位置的高亮显示。
 */
void Game::DrawBackGround() {
	static int selFieldAlpha = 255;          ///< 当前选择区域透明度
	static int selFieldDAlpha = -10;         ///< 透明度变化步长

	// 设置世界变换矩阵为单位矩阵（默认状态）
	driver->setTransform(irr::video::ETS_WORLD, irr::core::IdentityMatrix);

	bool drawField = false;                  ///< 是否需要绘制场地背景图
	int rule = (dInfo.duel_rule >= 4) ? 1 : 0;  ///< 判断使用的场地布局规则（新旧规则）

	// 根据配置决定是否绘制场地魔法卡图像
	if(gameConf.draw_field_spell) {
		int fieldcode1 = -1;                 // 玩家1场地魔法卡编码
		int fieldcode2 = -1;                 // 玩家2场地魔法卡编码

		// 检查双方场地魔法区是否有正面朝上的卡片
		if(dField.szone[0][5] && dField.szone[0][5]->position & POS_FACEUP)
			fieldcode1 = dField.szone[0][5]->code;
		if(dField.szone[1][5] && dField.szone[1][5]->position & POS_FACEUP)
			fieldcode2 = dField.szone[1][5]->code;

		int fieldcode = (fieldcode1 > 0) ? fieldcode1 : fieldcode2;

		// 若两个玩家都有不同的场地魔法卡，则分别绘制两张
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
		} else if(fieldcode > 0) {// 否则只绘制一张共同的场地魔法卡
			auto texture = imageManager.GetTextureField(fieldcode);
			if(texture) {
				drawField = true;
				matManager.mTexture.setTexture(0, texture);
				driver->setMaterial(matManager.mTexture);
				driver->drawVertexPrimitiveList(matManager.vFieldSpell, 4, matManager.iRectangle, 2);
			}
		}
	}

	// 设置并绘制主战场地面板纹理
	matManager.mTexture.setTexture(0, drawField ? imageManager.tFieldTransparent[rule] : imageManager.tField[rule]);
	driver->setMaterial(matManager.mTexture);
	driver->drawVertexPrimitiveList(matManager.vField, 4, matManager.iRectangle, 2);

	// 设置材质用于后续线条绘制
	driver->setMaterial(matManager.mBackLine);

	// 处理用户可以选择的位置高亮显示逻辑
	if(dInfo.curMsg == MSG_SELECT_PLACE || dInfo.curMsg == MSG_SELECT_DISFIELD || dInfo.curMsg == MSG_HINT) {
		float cv[4] = {0.0f, 0.0f, 1.0f, 1.0f};   // 颜色向量
		unsigned int filter = 0x1;                // 过滤器掩码

		// 遍历我方怪兽区
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldMzone[0][i], !(dField.selected_field & filter), 2, cv);
		}

		// 遍历我方魔法陷阱区
		filter = 0x100;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldSzone[0][i][rule], !(dField.selected_field & filter), 2, cv);
		}

		// 遍历对方怪兽区
		filter = 0x10000;
		for (int i = 0; i < 7; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldMzone[1][i], !(dField.selected_field & filter), 2, cv);
		}

		// 遍历对方魔法陷阱区
		filter = 0x1000000;
		for (int i = 0; i < 8; ++i, filter <<= 1) {
			if (dField.selectable_field & filter)
				DrawSelectionLine(matManager.vFieldSzone[1][i][rule], !(dField.selected_field & filter), 2, cv);
		}
	}

	// 显示双方总攻击力数值
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

	// 绘制被禁用的格子（以X形线表示）
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

	// 绘制当前鼠标悬停区域的选择效果
	if (dField.hovered_location != 0 && dField.hovered_location != 2 && dField.hovered_location != POSITION_HINT
		&& !(dInfo.duel_rule < 4 && dField.hovered_location == LOCATION_MZONE && dField.hovered_sequence > 4)
		&& !(dInfo.duel_rule >= 4 && dField.hovered_location == LOCATION_SZONE && dField.hovered_sequence > 5)) {
#ifdef _IRR_ANDROID_PLATFORM_
		if (dField.hovered_location == LOCATION_MZONE) {
			ClientCard* pcard = mainGame->dField.mzone[dField.hovered_controler][dField.hovered_sequence];
			if(pcard && pcard->type & TYPE_LINK) {
				DrawLinkedZones(pcard);       // 绘制连接怪兽所链接的区域
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
/**
 * @brief 绘制一张卡片（ClientCard）到屏幕上。
 *
 * 此函数负责处理卡片动画、渲染正面与背面纹理、选择高亮效果以及各种状态图标（如攻击标记、装备目标等）的绘制。
 * 它会根据卡片当前的状态（移动中、淡入淡出、是否被选中等）进行相应的图形变换和材质设置，并调用底层图形接口完成绘制。
 *
 * @param pcard 指向要绘制的 ClientCard 对象的指针。
 */
void Game::DrawCard(ClientCard* pcard) {
	// 处理卡片动画帧逻辑：如果仍在播放动画，则更新位置、旋转和透明度
	if(pcard->aniFrame) {
		// 更新移动中的位置和旋转
		if(pcard->is_moving) {
			pcard->curPos += pcard->dPos;
			pcard->curRot += pcard->dRot;
			pcard->mTransform.setTranslation(pcard->curPos);
			pcard->mTransform.setRotationRadians(pcard->curRot);
		}
		// 更新淡入/淡出时的透明度
		if(pcard->is_fading)
			pcard->curAlpha += pcard->dAlpha;
		pcard->aniFrame--;
		// 动画结束时重置相关标志位
		if(pcard->aniFrame == 0) {
			pcard->is_moving = false;
			pcard->is_fading = false;
			pcard->chain_code = 0;
		}
	}

	// 设置卡片材质颜色属性
	matManager.mCard.AmbientColor = 0xffffffff;
	matManager.mCard.DiffuseColor = (pcard->curAlpha << 24) | 0xffffff;

	// 应用世界变换矩阵并获取Z轴方向余弦值用于判断正反面显示
	driver->setTransform(irr::video::ETS_WORLD, pcard->mTransform);
	auto m22 = pcard->mTransform(2, 2);

	// 根据朝向决定是否绘制卡片正面
	if(m22 > -0.99 || pcard->is_moving) {
		auto code = pcard->code;
		if (code == 0 && pcard->is_moving)
			code = pcard->chain_code;
		matManager.mCard.setTexture(0, imageManager.GetTexture(code));
		driver->setMaterial(matManager.mCard);
		driver->drawVertexPrimitiveList(matManager.vCardFront, 4, matManager.iRectangle, 2);
	}

	// 根据朝向决定是否绘制卡片背面
	if(m22 < 0.99 || pcard->is_moving) {
		matManager.mCard.setTexture(0, imageManager.tCover[pcard->controler]);
		driver->setMaterial(matManager.mCard);
		driver->drawVertexPrimitiveList(matManager.vCardBack, 4, matManager.iRectangle, 2);
	}

	// 若卡片正在移动则跳过后续的选择框及特殊标记绘制
	if(pcard->is_moving)
		return;

	// 绘制可选中卡片的轮廓线
	if(pcard->is_selectable && (pcard->location & 0xe)) {
		float cv[4] = {1.0f, 1.0f, 0.0f, 1.0f};
		if((pcard->location == LOCATION_HAND && pcard->code) || ((pcard->location & 0xc) && (pcard->position & POS_FACEUP)))
			DrawSelectionLine(matManager.vCardOutline, !pcard->is_selected, 2, cv);
		else
			DrawSelectionLine(matManager.vCardOutliner, !pcard->is_selected, 2, cv);
	}

	// 绘制高亮卡片的轮廓线
	if(pcard->is_highlighting) {
		float cv[4] = {0.0f, 1.0f, 1.0f, 1.0f};
		if((pcard->location == LOCATION_HAND && pcard->code) || ((pcard->location & 0xc) && (pcard->position & POS_FACEUP)))
			DrawSelectionLine(matManager.vCardOutline, true, 2, cv);
		else
			DrawSelectionLine(matManager.vCardOutliner, true, 2, cv);
	}

	// 设置新的变换矩阵图层以绘制附加符号或标记
	irr::core::matrix4 im;
	im.setTranslation(pcard->curPos);
	driver->setTransform(irr::video::ETS_WORLD, im);

	// 绘制装备卡标记
	if(pcard->is_showequip) {
		matManager.mTexture.setTexture(0, imageManager.tEquip);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	}
	// 绘制效果对象的标记
	else if(pcard->is_showtarget) {
		matManager.mTexture.setTexture(0, imageManager.tTarget);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	}
	// 绘制连锁中对象的标记
	else if(pcard->is_showchaintarget) {
		matManager.mTexture.setTexture(0, imageManager.tChainTarget);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	}
	// 绘制无效化或者不能使用状态标记
	else if((pcard->status & (STATUS_DISABLED | STATUS_FORBIDDEN))
		&& (pcard->location & LOCATION_ONFIELD) && (pcard->position & POS_FACEUP)) {
		matManager.mTexture.setTexture(0, imageManager.tNegated);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);
	}

	// 绘制攻击命令标记
	if(pcard->cmdFlag & COMMAND_ATTACK) {
		matManager.mTexture.setTexture(0, imageManager.tAttack);
		driver->setMaterial(matManager.mTexture);
		irr::core::matrix4 atk;
		atk.setTranslation(pcard->curPos + irr::core::vector3df(0, (pcard->controler == 0 ? -1 : 1) * (atkdy / 4.0f + 0.35f), 0.05f));
		atk.setRotationRadians(irr::core::vector3df(0, 0, pcard->controler == 0 ? 0 : 3.1415926f));
		driver->setTransform(irr::video::ETS_WORLD, atk);
		driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);
	}

	// 绘制灵摆刻度图像（适用于 duel_rule >= 4 的情况）
	if(dInfo.duel_rule >= 4) {
		// 左侧灵摆区域（序列号为0）
		if (isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence == 0)) {
			int scale = pcard->lscale;
			matManager.mTexture.setTexture(0, imageManager.tLScale[scale]);
			driver->setMaterial(matManager.mTexture);
			driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
		}//pendulum LEFT scale image

		// 右侧灵摆区域（序列号为4）
		if (isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence == 4)) {
			int scale2 = pcard->rscale;
			matManager.mTexture.setTexture(0, imageManager.tRScale[scale2]);
			driver->setMaterial(matManager.mTexture);
			driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
		}//pendulum RIGHT scale image
	} else {// 兼容旧版灵摆规则下的额外灵摆区（序列大于5的情况）
		if(isPSEnabled && (pcard->type & TYPE_PENDULUM) && ((pcard->location & LOCATION_SZONE) && pcard->sequence > 5)) {
			int scale = pcard->sequence == 6 ? pcard->lscale : pcard->rscale;
			matManager.mTexture.setTexture(0, pcard->sequence == 6 ? imageManager.tLScale[scale] : imageManager.tRScale[scale]);
			driver->setMaterial(matManager.mTexture);
			driver->drawVertexPrimitiveList(matManager.vPScale, 4, matManager.iRectangle, 2);
		}
	}
}
/**
 * @brief 绘制带阴影效果的文字
 *
 * 该函数通过先绘制阴影文字再绘制前景文字的方式实现文字阴影效果。
 * 阴影位置通过padding参数进行偏移调整。
 *
 * @tparam T 文字内容类型，通常为字符串类型
 * @param font 用于绘制文字的字体对象
 * @param text 要绘制的文字内容
 * @param position 文字绘制的位置矩形区域
 * @param padding 阴影偏移量设置，用于控制阴影相对于主文字的位置
 * @param color 主文字的颜色
 * @param shadowcolor 阴影文字的颜色
 * @param hcenter 水平居中标志，true表示水平居中绘制
 * @param vcenter 垂直居中标志，true表示垂直居中绘制
 * @param clip 可选的裁剪区域，限制文字绘制范围
 */
template<typename T>
void Game::DrawShadowText(irr::gui::CGUITTFont* font, const T& text, const irr::core::rect<irr::s32>& position, const irr::core::rect<irr::s32>& padding,
			irr::video::SColor color, irr::video::SColor shadowcolor, bool hcenter, bool vcenter, const irr::core::rect<irr::s32>* clip) {
	// 计算阴影文字的绘制位置，基于原始位置减去padding偏移量
	irr::core::rect<irr::s32> shadowposition = irr::core::recti(position.UpperLeftCorner.X - padding.UpperLeftCorner.X, position.UpperLeftCorner.Y - padding.UpperLeftCorner.Y,
										   position.LowerRightCorner.X - padding.LowerRightCorner.X, position.LowerRightCorner.Y - padding.LowerRightCorner.Y);
	// 先绘制阴影文字
	font->drawUstring(text, shadowposition, shadowcolor, hcenter, vcenter, clip);
	// 再绘制主文字，覆盖在阴影之上形成阴影效果
	font->drawUstring(text, position, color, hcenter, vcenter, clip);
}
/**
 * @brief 绘制带有阴影效果的粗体文本
 *
 * 该函数通过在原始文本周围绘制多个阴影字符来创建粗体/阴影效果
 *
 * @param font 指向 irr::gui::CGUITTFont 字体对象的指针，用于文本渲染
 * @param text 要绘制的文本内容，模板类型支持多种文本格式
 * @param position 文本绘制的位置矩形区域
 * @param color 原始文本的颜色
 * @param shadowcolor 阴影文本的颜色
 * @param hcenter 是否水平居中对齐
 * @param vcenter 是否垂直居中对齐
 * @return void 无返回值
 */
template<typename T>
void Game::DrawBoldText(irr::gui::CGUITTFont* font, const T& text, const irr::core::rect<irr::s32>& position,
                              irr::video::SColor color, irr::video::SColor shadowcolor, bool hcenter, bool vcenter) {
    // 绘制八个方向的阴影字符：左、右、上、下、左上、右上、左下、右下
    font->drawUstring(text, irr::core::recti(position.UpperLeftCorner.X-1, position.UpperLeftCorner.Y, position.LowerRightCorner.X-1, position.LowerRightCorner.Y), shadowcolor, hcenter, vcenter);
    font->drawUstring(text, irr::core::recti(position.UpperLeftCorner.X+1, position.UpperLeftCorner.Y, position.LowerRightCorner.X+1, position.LowerRightCorner.Y), shadowcolor, hcenter, vcenter);
    font->drawUstring(text, irr::core::recti(position.UpperLeftCorner.X, position.UpperLeftCorner.Y-1, position.LowerRightCorner.X, position.LowerRightCorner.Y-1), shadowcolor, hcenter, vcenter);
    font->drawUstring(text, irr::core::recti(position.UpperLeftCorner.X, position.UpperLeftCorner.Y+1, position.LowerRightCorner.X, position.LowerRightCorner.Y+1), shadowcolor, hcenter, vcenter);
    // 最后绘制原始字符
    font->drawUstring(text, position, color, hcenter, vcenter);
}

/**
 * @brief 绘制游戏中的各种辅助元素和界面信息。
 *
 * 此函数负责绘制游戏中的一些动态效果、按钮高亮、生命值条、玩家头像、时间显示、卡牌状态等杂项内容。
 * 包括发动效果、连锁符号、墓地/除外区域的旋转图案遮罩层、LP动画、回合数、玩家名、卡牌数量统计等。
 */
void Game::DrawMisc() {
	static irr::core::vector3df act_rot(0, 0, 0); // 激活动画旋转角度（静态变量）
	int rule = (dInfo.duel_rule >= 4) ? 1 : 0;     // 判断决斗规则版本

	// 初始化变换矩阵
	irr::core::matrix4 im, ic, it, ig;

	// 更新激活动画旋转角度
	act_rot.Z += 0.02f;
	im.setRotationRadians(act_rot);

	// 设置材质并绑定纹理
	matManager.mTexture.setTexture(0, imageManager.tAct);
	driver->setMaterial(matManager.mTexture);

	// 遍历两名玩家，绘制各个区域的激活提示
	for(int player = 0; player < 2; ++player) {
		// 绘制主卡组可发动的提示
		if(dField.deck_act[player]) {
			im.setTranslation(irr::core::vector3df(
				(matManager.vFieldDeck[player][0].Pos.X + matManager.vFieldDeck[player][1].Pos.X) / 2,
				(matManager.vFieldDeck[player][0].Pos.Y + matManager.vFieldDeck[player][2].Pos.Y) / 2,
				dField.deck[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}

		// 绘制墓地可发动的提示
		if(dField.grave_act[player]) {
			im.setTranslation(irr::core::vector3df(
				(matManager.vFieldGrave[player][rule][0].Pos.X + matManager.vFieldGrave[player][rule][1].Pos.X) / 2,
				(matManager.vFieldGrave[player][rule][0].Pos.Y + matManager.vFieldGrave[player][rule][2].Pos.Y) / 2,
				dField.grave[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}

		// 绘制除外状态可发动的提示
		if(dField.remove_act[player]) {
			im.setTranslation(irr::core::vector3df(
				(matManager.vFieldRemove[player][rule][0].Pos.X + matManager.vFieldRemove[player][rule][1].Pos.X) / 2,
				(matManager.vFieldRemove[player][rule][0].Pos.Y + matManager.vFieldRemove[player][rule][2].Pos.Y) / 2,
				dField.remove[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}

		// 绘制额外卡组可发动、特殊召唤的提示
		if(dField.extra_act[player]) {
			im.setTranslation(irr::core::vector3df(
				(matManager.vFieldExtra[player][0].Pos.X + matManager.vFieldExtra[player][1].Pos.X) / 2,
				(matManager.vFieldExtra[player][0].Pos.Y + matManager.vFieldExtra[player][2].Pos.Y) / 2,
				dField.extra[player].size() * 0.01f + 0.02f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}

		// 绘制灵摆区域可发动、灵摆召唤的提示
		if(dField.pzone_act[player]) {
			int seq = dInfo.duel_rule >= 4 ? 0 : 6;
			im.setTranslation(irr::core::vector3df(
				(matManager.vFieldSzone[player][seq][rule][0].Pos.X + matManager.vFieldSzone[player][seq][rule][1].Pos.X) / 2,
				(matManager.vFieldSzone[player][seq][rule][0].Pos.Y + matManager.vFieldSzone[player][seq][rule][2].Pos.Y) / 2,
				0.03f));
			driver->setTransform(irr::video::ETS_WORLD, im);
			driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
		}
	}

	// 绘制场上需要效果处理的卡片提示旋转图标
	if(dField.conti_act) {
		irr::core::vector3df pos = irr::core::vector3df(
			(matManager.vFieldContiAct[0].X + matManager.vFieldContiAct[1].X) / 2,
			(matManager.vFieldContiAct[0].Y + matManager.vFieldContiAct[2].Y) / 2,
			0);

		im.setRotationRadians(irr::core::vector3df(0, 0, 0));

		// 绘制每张需要效果处理的卡
		for(auto cit = dField.conti_cards.begin(); cit != dField.conti_cards.end(); ++cit) {
			im.setTranslation(pos);
			driver->setTransform(irr::video::ETS_WORLD, im);
			matManager.mCard.setTexture(0, imageManager.GetTexture((*cit)->code));
			driver->setMaterial(matManager.mCard);
			driver->drawVertexPrimitiveList(matManager.vCardFront, 4, matManager.iRectangle, 2);
			pos.Z += 0.03f;
		}

		// 最后绘制需要效果处理的卡的旋转图标
		im.setTranslation(pos);
		im.setRotationRadians(act_rot);
		driver->setTransform(irr::video::ETS_WORLD, im);
		driver->setMaterial(matManager.mTexture);
		driver->drawVertexPrimitiveList(matManager.vActivate, 4, matManager.iRectangle, 2);
	}

	// 绘制连锁旋转图标与连锁序号数字
	if(dField.chains.size() > 1 || mainGame->gameConf.draw_single_chain) {
		for(size_t i = 0; i < dField.chains.size(); ++i) {
			if(dField.chains[i].solved)
				break;

			// 设置连锁旋转图标材质和位置
			matManager.mTRTexture.setTexture(0, imageManager.tChain);
			matManager.mTRTexture.AmbientColor = 0xffffff00;
			ic.setRotationRadians(act_rot);
			ic.setTranslation(dField.chains[i].chain_pos);
			driver->setMaterial(matManager.mTRTexture);
			driver->setTransform(irr::video::ETS_WORLD, ic);
			driver->drawVertexPrimitiveList(matManager.vSymbol, 4, matManager.iRectangle, 2);

			// 绘制连锁序号数字
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

	// 绘制无法查看墓地的遮罩
	if(dField.cant_check_grave) {
		matManager.mTexture.setTexture(0, imageManager.tNegated);
		driver->setMaterial(matManager.mTexture);

		// 我方玩家墓地遮罩
		ig.setTranslation(irr::core::vector3df(
			(matManager.vFieldGrave[0][rule][0].Pos.X + matManager.vFieldGrave[0][rule][1].Pos.X) / 2,
			(matManager.vFieldGrave[0][rule][0].Pos.Y + matManager.vFieldGrave[0][rule][2].Pos.Y) / 2,
			dField.grave[0].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, ig);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);

		// 对方玩家墓地遮罩
		ig.setTranslation(irr::core::vector3df(
			(matManager.vFieldGrave[1][rule][0].Pos.X + matManager.vFieldGrave[1][rule][1].Pos.X) / 2,
			(matManager.vFieldGrave[1][rule][0].Pos.Y + matManager.vFieldGrave[1][rule][2].Pos.Y) / 2,
			dField.grave[1].size() * 0.01f + 0.02f));
		driver->setTransform(irr::video::ETS_WORLD, ig);
		driver->drawVertexPrimitiveList(matManager.vNegate, 4, matManager.iRectangle, 2);
	}

	// 绘制提醒完成选择按钮的旋转虚线边框
	if(btnCancelOrFinish->isVisible() && dField.select_ready)
		DrawSelectionLine(btnCancelOrFinish, 4, 0xff00ff00);

	// 绘制离开游戏/投降按钮的旋转虚线边框
	if(btnLeaveGame->isVisible() && dField.tag_teammate_surrender)
		DrawSelectionLine(btnLeaveGame, 4, 0xff00ff00);

	// 绘制生命值条
	if(dInfo.start_lp) {
		auto maxLP = dInfo.isTag ? dInfo.start_lp / 2 : dInfo.start_lp;

		// 我方玩家 LP条
		if(dInfo.lp[0] >= maxLP) {
			auto layerCount = dInfo.lp[0] / maxLP;
			auto partialLP = dInfo.lp[0] % maxLP;
			auto bgColorPos = (layerCount - 1) % 5;
			auto fgColorPos = layerCount % 5;
			driver->draw2DImage(imageManager.tLPBar, Resize(390 + 235 * partialLP / maxLP, 12, 625, 74),
				irr::core::recti(0, bgColorPos * 60, 60, (bgColorPos + 1) * 60), 0, 0, true);
			if(partialLP > 0) {
				driver->draw2DImage(imageManager.tLPBar, Resize(390, 12, 390 + 235 * partialLP / maxLP, 74),
					irr::core::recti(0, fgColorPos * 60, 60, (fgColorPos + 1) * 60), 0, 0, true);
			}
		} else {
			driver->draw2DImage(imageManager.tLPBar, Resize(390, 12, 390 + 235 * dInfo.lp[0] / maxLP, 74),
				irr::core::recti(0, 0, 60, 60), 0, 0, true);
		}

		// 对方玩家 LP条
		if(dInfo.lp[1] >= maxLP) {
			auto layerCount = dInfo.lp[1] / maxLP;
			auto partialLP = dInfo.lp[1] % maxLP;
			auto bgColorPos = (layerCount - 1) % 5;
			auto fgColorPos = layerCount % 5;
			driver->draw2DImage(imageManager.tLPBar, Resize(695, 12, 930 - 235 * partialLP / maxLP, 74),
				irr::core::recti(0, bgColorPos * 60, 60, (bgColorPos + 1) * 60), 0, 0, true);
			if(partialLP > 0) {
				driver->draw2DImage(imageManager.tLPBar, Resize(930 - 235 * partialLP / maxLP, 12, 930, 74),
					irr::core::recti(0, fgColorPos * 60, 60, (fgColorPos + 1) * 60), 0, 0, true);
			}
		} else {
			driver->draw2DImage(imageManager.tLPBar, Resize(930 - 235 * dInfo.lp[1] / maxLP, 12, 930, 74),
				irr::core::recti(0, 0, 60, 60), 0, 0, true);
		}
	}

	// 处理LP变化动画
	if(lpframe) {
		dInfo.lp[lpplayer] -= lpd;
		myswprintf(dInfo.strLP[lpplayer], L"%d", dInfo.lp[lpplayer]);
		lpccolor -= 0x19000000;
		lpframe--;
	}

	// 显示LP变化文字
	if(lpcstring.size()) {
		if(lpplayer == 0) {
			DrawShadowText(lpcFont, lpcstring, Resize(400, 470, 920, 520), Resize(0, 2, 2, 0), lpccolor, lpccolor | 0x00ffffff, true, false);
		} else {
			DrawShadowText(lpcFont, lpcstring, Resize(400, 160, 920, 210), Resize(0, 2, 2, 0), lpccolor, lpccolor | 0x00ffffff, true, false);
		}
	}

	// 绘制双方玩家头像
	driver->draw2DImage(imageManager.tAvatar[0], Resize(335, 15, 390, 70), irr::core::recti(0, 0, 128, 128), 0, 0, true);
	driver->draw2DImage(imageManager.tAvatar[1], Resize(930, 15, 985, 70), irr::core::recti(0, 0, 128, 128), 0, 0, true);

	// 根据当前哪个玩家的回合决定LP条样式，回合玩家的为彩色，非回合玩家的为灰色
	if((dInfo.turn % 2 && dInfo.isFirst) || (!(dInfo.turn % 2) && !dInfo.isFirst)) {
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(327, 8, 630, 78), irr::core::recti(0, 0, 305, 70), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(689, 8, 991, 78), irr::core::recti(0, 210, 305, 280), 0, 0, true);
	} else {
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(327, 8, 630, 78), irr::core::recti(0, 70, 305, 140), 0, 0, true);
		driver->draw2DImage(imageManager.tLPBarFrame, Resize(689, 8, 991, 78), irr::core::recti(0, 140, 305, 210), 0, 0, true);
	}

	// 时间显示相关，显示倒计时图标，剩余秒数数字
	if(!dInfo.isReplay && dInfo.player_type < 7 && dInfo.time_limit) {
		if(imageManager.tClock) {
			driver->draw2DImage(imageManager.tClock, Resize(577, 50, 595, 68), irr::core::recti(0, 0, 34, 34), 0, 0, true);
			driver->draw2DImage(imageManager.tClock, Resize(695, 50, 713, 68), irr::core::recti(0, 0, 34, 34), 0, 0, true);
		}
		DrawShadowText(numFont, dInfo.str_time_left[0], Resize(595, 49, 625, 68), Resize(0, 1, 2, 0), dInfo.time_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_time_left[1], Resize(713, 49, 743, 68), Resize(0, 1, 2, 0), dInfo.time_color[1], 0xff000000, true, false);

		driver->draw2DImage(imageManager.tCover[2], Resize(537, 51, 550, 70), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[3], Resize(745, 51, 758, 70), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], Resize(550, 49, 575, 68), Resize(0, 1, 2, 0), dInfo.card_count_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_card_count[1], Resize(757, 49, 782, 68), Resize(0, 1, 2, 0), dInfo.card_count_color[1], 0xff000000, true, false);
	} else {
		driver->draw2DImage(imageManager.tCover[2], Resize(588, 48, 601, 68), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);
		driver->draw2DImage(imageManager.tCover[3], Resize(697, 48, 710, 68), irr::core::rect<irr::s32>(0, 0, CARD_IMG_WIDTH, CARD_IMG_HEIGHT), 0, 0, true);

		DrawShadowText(numFont, dInfo.str_card_count[0], Resize(600, 51, 625, 70), Resize(0, 1, 2, 0), dInfo.card_count_color[0], 0xff000000, true, false);
		DrawShadowText(numFont, dInfo.str_card_count[1], Resize(710, 51, 735, 70), Resize(0, 1, 2, 0), dInfo.card_count_color[1], 0xff000000, true, false);
	}

	// 绘制当前LP数值
	DrawShadowText(numFont, dInfo.strLP[0], Resize(305, 49, 614, 68), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);
	DrawShadowText(numFont, dInfo.strLP[1], Resize(711, 50, 1012, 69), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

	// 绘制玩家名称
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

	// 绘制回合数背景灰白渐变色矩形
	driver->draw2DRectangle(Resize(632, 10, 688, 30), 0x00000000, 0x00000000, 0xffffffff, 0xffffffff);
	driver->draw2DRectangle(Resize(632, 30, 688, 50), 0xffffffff, 0xffffffff, 0x00000000, 0x00000000);

	// 绘制当前回合数字
	DrawShadowText(lpcFont, dataManager.GetNumString(dInfo.turn), Resize(635, 5, 685, 40), Resize(0, 0, 2, 0), 0x80000000, 0x8000ffff, true, false);

	// 绘制场上怪兽状态
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

	// 绘制灵摆刻度数字（角上的数字，并非贴图）
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

	// 绘制各区域卡片数量
    //我方额外卡组总数量统计以及（额外表侧表示的卡）
	if(dField.extra[0].size()) {
		int offset = (dField.extra[0].size() >= 10) ? 0 : mainGame->numFont->getDimension(dataManager.GetNumString(1)).Width;
		DrawShadowText(numFont, dataManager.GetNumString(dField.extra[0].size()), Resize(320 + offset, 562, 371, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		DrawShadowText(numFont, dataManager.GetNumString(dField.extra_p_count[0], true), Resize(340, 562, 391, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
    //我方卡组总数量统计
	if(dField.deck[0].size()) {
		DrawShadowText(numFont, dataManager.GetNumString(dField.deck[0].size()), Resize(907, 562, 1021, 552), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
    //绘制我方在不同规则下的墓地、除外状态的总数量统计
	if(rule == 0) {
		if(dField.grave[0].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.grave[0].size()), Resize(837, 375, 984, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if(dField.remove[0].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.remove[0].size()), Resize(1015, 375, 957, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	} else {
		if(dField.grave[0].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.grave[0].size()), Resize(870, 456, 1002, 461), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if(dField.remove[0].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.remove[0].size()), Resize(837, 375, 984, 380), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	}
    //对方额外卡组总数量统计以及（额外表侧表示的卡）
	if(dField.extra[1].size()) {
		int offset = (dField.extra[1].size() >= 10) ? 0 : mainGame->numFont->getDimension(dataManager.GetNumString(1)).Width;
		DrawShadowText(numFont, dataManager.GetNumString(dField.extra[1].size()), Resize(808 + offset, 207, 898, 232), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		DrawShadowText(numFont, dataManager.GetNumString(dField.extra_p_count[1], true), Resize(828, 207, 918, 232), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
    //我方卡组总数量统计
	if(dField.deck[1].size()) {
		DrawShadowText(numFont, dataManager.GetNumString(dField.deck[1].size()), Resize(465, 207, 481, 232), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
	}
    //绘制对方在不同规则下的墓地、除外状态的总数量统计
	if(rule == 0) {
		if(dField.grave[1].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.grave[1].size()), Resize(420, 310, 462, 281), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if(dField.remove[1].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.remove[1].size()), Resize(300, 310, 443, 340), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	} else {
		if(dField.grave[1].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.grave[1].size()), Resize(455, 249, 462, 299), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
		if(dField.remove[1].size()) {
			DrawShadowText(numFont, dataManager.GetNumString(dField.remove[1].size()), Resize(420, 310, 462, 281), Resize(1, 1, 1, 1), 0xffffff00, 0xff000000, true, false, 0);
		}
	}
}
/**
 * @brief 绘制卡片的状态信息（攻击力、防御力或连接数、等级等）
 *
 * 此函数用于在指定位置绘制一张卡片的攻击/防御数值以及等级等相关状态文字。
 * 攻击力和防御力会根据当前值与基础值比较显示不同颜色：
 * - 高于基础值：黄色 (0xffffff00)
 * - 低于基础值：粉色 (0xffff2090)
 * - 等于基础值：白色 (0xffffffff)
 * 对于连接怪兽（TYPE_LINK），将只显示连接数；否则显示防御力和等级。
 * 等级的颜色依据卡片类型变化：
 * - 超量怪兽：紫色 (0xffff80ff)
 * - 调整怪兽（Tuner）：黄色 (0xffffff00)
 * - 其他：白色 (0xffffffff)
 *
 * @param pcard 指向要绘制状态的客户端卡片对象
 * @param x1 左侧起始横坐标
 * @param y1 上方起始纵坐标
 * @param x2 右侧结束横坐标
 * @param y2 下方结束纵坐标
 */
void Game::DrawStatus(ClientCard* pcard, int x1, int y1, int x2, int y2) {
	// 绘制分隔符 "/"
	DrawShadowText(adFont, L"/", Resize(x1 - 3, y1 + 1, x1 + 5, y1 + 21), Resize(1, 1, 1, 1), 0xffffffff, 0xff000000, true, false, 0);

	// 获取并绘制攻击力字符串，并根据是否增强着色
	int w = adFont->getDimension(pcard->atkstring).Width;
	DrawShadowText(adFont, pcard->atkstring, Resize(x1 - 4, y1 + 1, x1 - 4, y1 + 21, -w, 0, 0, 0), Resize(1, 1, 1, 1),
		pcard->attack > pcard->base_attack ? 0xffffff00 : pcard->attack < pcard->base_attack ? 0xffff2090 : 0xffffffff, 0xff000000);

	// 判断是否是连接卡，分别处理连接数或防御力+等级的绘制
	if(pcard->type & TYPE_LINK) {
		// 连接卡仅绘制连接数
		w = adFont->getDimension(pcard->linkstring).Width;
		DrawShadowText(adFont, pcard->linkstring, Resize(x1 + 5, y1 + 1, x1 + 5, y1 + 21, 0, 0, w, 0), Resize(1, 1, 1, 1), 0xff99ffff);
	} else {
		// 非连接卡绘制防御力
		w = adFont->getDimension(pcard->defstring).Width;
		DrawShadowText(adFont, pcard->defstring, Resize(x1 + 5, y1 + 1, x1 + 5, y1 + 21, 0, 0, w, 0), Resize(1, 1, 1, 1),
			pcard->defense > pcard->base_defense ? 0xffffff00 : pcard->defense < pcard->base_defense ? 0xffff2090 : 0xffffffff);

		// 绘制等级/阶级文字，根据不同类型使用不同颜色
		DrawShadowText(adFont, pcard->lvstring, Resize(x2 + 1, y2, x2 + 3, y2 + 21), Resize(1, 1, 1, 1),
			(pcard->type & TYPE_XYZ) ? 0xffff80ff : (pcard->type & TYPE_TUNER) ? 0xffffff00 : 0xffffffff);
	}
}
/**
 * @brief 绘制游戏界面中的图形用户界面（GUI）元素。
 *
 * 此函数负责处理图像加载队列中待设置的图像，并更新所有正在进行淡入淡出动画的 GUI 元素的位置与可见性。
 * 同时也会绘制整个 GUI 环境。
 */
void Game::DrawGUI() {
	// 处理等待加载的图像资源：将已加载的纹理设置到对应的 GUI 元素上
	while (imageLoading.size()) {
		auto mit = imageLoading.cbegin();
		mit->first->setImage(imageManager.GetTexture(mit->second));
		imageLoading.erase(mit);
	}

	// 遍历并更新所有正在执行淡入或淡出效果的 GUI 元素
	for(auto fit = fadingList.begin(); fit != fadingList.end();) {
		auto fthis = fit++;
		FadingUnit& fu = *fthis;

		// 如果当前元素仍在进行淡入/淡出动画
		if(fu.fadingFrame) {
			fu.guiFading->setVisible(true);

			// 淡入逻辑
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
						// 根据不同窗口恢复按钮图片显示状态
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
			}
			// 淡出逻辑
			else {
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
						// 根据不同窗口恢复按钮图片显示状态
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
				// 若需要发送响应信号且动画结束，则触发客户端消息发送
				if(fu.signalAction && !fu.fadingFrame) {
					DuelClient::SendResponse();
					fu.signalAction = false;
				}
			}
		}
		// 自动淡出计时器处理
		else if(fu.autoFadeoutFrame) {
			fu.autoFadeoutFrame--;
			if(!fu.autoFadeoutFrame)
				HideElement(fu.guiFading);
		}
		// 动画完全结束后从列表移除该单元
		else
			fadingList.erase(fthis);
	}

	// 最后统一绘制整个 GUI 场景
	env->drawAll();
}
/**
 * @brief 绘制游戏中的特殊效果、动画以及聊天信息。
 *
 * 此函数负责处理游戏中各种卡片展示效果（如召唤、发动、无效等）、攻击箭头动画，
 * 以及屏幕上的聊天消息显示。根据不同的状态变量（如 showcard、is_attacking）来决定绘制的内容。
 *
 * 主要功能包括：
 * - 根据 showcard 的不同值绘制对应的卡片特效或动画；
 * - 在卡片发动时绘制光带闪过的渐变效果；
 * - 显示无效化效果的遮罩和无效图标；
 * - 淡入/缩放方式展示卡片；
 * - 展示猜拳界面与阶段提示文字；
 * - 渲染攻击箭头动画；
 * - 控制并渲染聊天窗口内容。
 */
void Game::DrawSpec() {
    DrawEmoticon();
    // 如果需要展示卡片，则进行相关绘图操作
    if(showcard) {
        // 获取当前要展示的卡片纹理
        irr::video::ITexture* showimg = imageManager.GetTexture(showcardcode);
        if(showimg == NULL)
            return;

        // 获取原始图片尺寸
        irr::core::dimension2d<irr::u32> orisize = showimg->getOriginalSize();

        // 根据 showcard 值选择不同的展示效果
        switch(showcard) {
        case 1: { // 展示正在激活的效果：从上往下逐渐揭开遮罩
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
        case 2: { // 遮罩继续向右展开直到完全消失
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
            driver->draw2DImage(imageManager.tMask, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale + showcarddif * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale),
                                irr::core::recti(0, 0, CARD_IMG_WIDTH - showcarddif, CARD_IMG_HEIGHT), 0, 0, true);
            showcarddif += 15;
            if(showcarddif >= CARD_IMG_WIDTH) {
                showcard = 0;
            }
            break;
        }
        case 3: { // 否定效果：中心出现一个缩小的否定图标
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
            driver->draw2DImage(imageManager.tNegated, irr::core::recti(660 * xScale - 130 * yScale + showcarddif * yScale, (141 + showcarddif) * yScale, 660 * xScale + 130 * yScale - showcarddif * yScale, (397 - showcarddif) * yScale), irr::core::recti(0, 0, 128, 128), 0, 0, true);
            if(showcarddif < 64)
                showcarddif += 4;
            break;
        }
        case 4: { // 卡片淡入效果：透明度逐步增加到不透明//
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
        case 5: { // 特殊召唤效果：卡片从小变大同时淡入
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
        case 6: { // 时间计数器效果：数字逐渐放大
            driver->draw2DImage(showimg, irr::core::recti(660 * xScale - (CARD_IMG_WIDTH / 2) * yScale, 150 * yScale, 660 * xScale + (CARD_IMG_WIDTH / 2) * yScale, (150 + CARD_IMG_HEIGHT) * yScale), irr::core::recti(0, 0, orisize.Width, orisize.Height), 0, 0, true);
            driver->draw2DImage(imageManager.tNumber, irr::core::recti(660 * xScale - 130 * yScale + showcarddif * yScale, (141 + showcarddif) * yScale, 660 * xScale + 130 * yScale - showcarddif * yScale, (397 - showcarddif) * yScale),
                                irr::core::recti((showcardp % 5) * 64, (showcardp / 5) * 64, (showcardp % 5 + 1) * 64, (showcardp / 5 + 1) * 64), 0, 0, true);
            if(showcarddif < 64)
                showcarddif += 4;
            break;
        }
        case 7: { // 普通召唤效果：卡片翻转进入画面
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
        case 100: { // 猜拳按钮展示效果：两个手型图案上下移动
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
        case 101: { // 阶段提示文字展示效果：淡入→停留→淡出
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

    // 如果处于攻击状态，则绘制攻击箭头动画
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

    // 处理聊天框是否可见逻辑
    bool showChat = true;
    if(hideChat) {
        showChat = false;
        hideChatTimer = 10;
    } else if(hideChatTimer > 0) {
        showChat = false;
        hideChatTimer--;
    }

    // 绘制聊天信息区域
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
/**
 * @brief 绘制卡片缩略图及其限制信息和可用性标记。
 *
 * 此函数用于在游戏界面中绘制一张卡片的缩略图，并根据当前的限制列表（如禁限卡表）以及卡片的可用性，
 * 在图像上叠加相应的图标。支持拖拽状态下的特殊显示。
 *
 * @param cp 指向卡片数据的指针，包含原始卡号和别名等信息。
 * @param pos 图像绘制的位置坐标（左上角）。
 * @param lflist 当前使用的限制列表（例如OCG/TCG禁限卡表）。
 * @param drag 是否处于拖拽状态，影响图像绘制区域大小。
 */
void Game::DrawThumb(code_pointer cp, irr::core::vector2di pos, const LFList* lflist, bool drag) {
	// 获取卡牌卡号和别名卡号，如果有别名则使用别名
    auto code = cp->first;
	auto lcode = cp->second.alias;
	if(lcode == 0)
		lcode = code;

	// 获取卡片纹理图像
	irr::video::ITexture* img = imageManager.GetTexture(code);
	if(img == nullptr)
		return; // 防止空指针访问导致崩溃

	// 获取图像原始尺寸
	irr::core::dimension2d<irr::u32> size = img->getOriginalSize();

	// 计算绘制位置，适配不同屏幕比例
	float x1 = pos.X + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2; // 左侧边界调整
	float x2 = pos.X + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2; // 右侧边界调整

	// 定义各个部分的绘制矩形区域
	irr::core::recti dragloc = irr::core::recti(x1, pos.Y, x2, pos.Y + CARD_THUMB_HEIGHT * mainGame->yScale); // 主图区域
	irr::core::recti limitloc = irr::core::recti(x1, pos.Y, x1 + 20 * mainGame->yScale, pos.Y + 20 * mainGame->yScale); // 限制标识区域
	irr::core::recti otloc = irr::core::recti(x1, pos.Y + 50 * mainGame->yScale, x1 + 30 * mainGame->yScale, pos.Y + 65 * mainGame->yScale); // OT类型标识区域

	// 绘制主图
	driver->draw2DImage(img, dragloc, irr::core::rect<irr::s32>(0, 0, size.Width, size.Height));

	// 查找并绘制限制等级图标
	auto lfit = lflist->content.find(lcode);
	if (lfit != lflist->content.end()) {
		switch(lfit->second) {
		case 0:
			driver->draw2DImage(imageManager.tLimit, limitloc, irr::core::recti(0, 0, 64, 64), 0, 0, true);
			break;
		case 1:
			driver->draw2DImage(imageManager.tLimit, limitloc, irr::core::recti(64, 0, 128, 64), 0, 0, true);
            DrawBoldText(icFont, L"1", limitloc, 0xffffff00, 0xffffff00, true, true);
            break;
		case 2:
			driver->draw2DImage(imageManager.tLimit, limitloc, irr::core::recti(64, 0, 128, 64), 0, 0, true);
            DrawBoldText(icFont, L"2", limitloc, 0xffffff00, 0xffffff00, true, true);
			break;
		}
	}

    // 获取卡片点数，并不能简单判断是否有alias，而是要判断是否是异画还是规则上同名的不同卡，TODO 暂定最大差异值是20，因为目前单张卡异画数量还未到这个值，未来很大可能会出现更多，需要即时调整
	auto lfcredit = lflist->credits.find(cp->second.alias && abs(static_cast<int>(cp->second.alias) - static_cast<int>(cp->first)) <= 20 ? cp->second.alias : cp->first);
    if(lfcredit != lflist->credits.end()) {
        for(auto& credit_entry : lfcredit->second) {
            auto value = credit_entry.second;
            driver->draw2DImage(imageManager.tLimit, limitloc, irr::core::recti(0, 64, 64, 128), 0, 0, true);
            if (value > -10 || value < 100) {//数字只两个占位符（-9~99）时用攻守数字正好，否则就用更迷你的字体
                DrawBoldText(adFont, std::to_wstring(static_cast<int>(value)), limitloc, 0xff00ffff, 0xff00ffff, true, true);
            } else {
                DrawBoldText(miniFont, std::to_wstring(static_cast<int>(value)), limitloc, 0xff00ffff, 0xff00ffff, true, true);
            }
        }
    }
	// 判断是否需要显示可用性相关图标
	bool showAvail = false;
	bool showNotAvail = false;
	int filter_lm = cbLimit->getSelected();
	bool avail = !((filter_lm == 5 && !(cp->second.ot & AVAIL_OCG)
				|| (filter_lm == 6 && !(cp->second.ot & AVAIL_TCG))
				|| (filter_lm == 7 && !(cp->second.ot & AVAIL_SC))
				|| (filter_lm == 8 && !(cp->second.ot & AVAIL_CUSTOM))
				|| (filter_lm == 9 && (cp->second.ot & AVAIL_OCGTCG) != AVAIL_OCGTCG)));

	if(filter_lm >= 5) {
		showAvail = avail;
		showNotAvail = !avail;
	} else if(!(cp->second.ot & gameConf.defaultOT)) {
		showNotAvail = true;
	}

	// 根据可用性状态绘制对应图标
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
			driver->draw2DImage(imageManager.tLimit, otloc, irr::core::recti(64, 0, 128, 64), 0, 0, true);
	}
}

/**
 * @brief 绘制卡组构建界面中的卡组信息区域，包括主卡组、额外卡组、副卡组以及搜索结果等。
 *
 * 此函数负责绘制当前卡组中各个部分（主卡组、额外卡组、副卡组）的卡片缩略图，并显示各类型卡片的数量统计。
 * 同时还绘制了搜索结果列表及拖拽状态下的卡片预览。
 */
void Game::DrawDeckBd() {
	wchar_t textBuffer[64];

	// 主卡组区域背景渐变色与白色边框
	int mainsize = deckManager.current_deck.main.size();
	driver->draw2DRectangle(Resize(310, 136, 410, 157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(Resize(309, 136, 410, 157));

	// 显示“主卡组”的文字
    DrawShadowText(guiFont, dataManager.GetSysString(deckBuilder.showing_pack ? 1477 : 1330), Resize(300, 136, 395, 156), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

	// 显示主卡组数量数字
    DrawShadowText(numFont, dataManager.GetNumString(mainsize), Resize(360, 137, 420, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

	// 类型计数区域背景与边框
	driver->draw2DRectangle(Resize(638, 137, 798, 157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
	driver->draw2DRectangleOutline(Resize(637, 136, 798, 157));

	// 怪兽卡数量图标与文本
	driver->draw2DImage(imageManager.tCardType, Resize(645, 136, 645+14+3/8, 156), irr::core::recti(0, 0, 23, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_MONSTER)), Resize(670, 137, 690, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

	// 魔法卡数量图标与文本
	driver->draw2DImage(imageManager.tCardType, Resize(695, 136, 695+14+3/8, 156), irr::core::recti(23, 0, 46, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_SPELL)), Resize(720, 138, 740, 158), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

	// 陷阱卡数量图标与文本
	driver->draw2DImage(imageManager.tCardType, Resize(745, 136, 745+14+3/8, 156), irr::core::recti(46, 0, 69, 32), 0, 0, true);
    DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.main, TYPE_TRAP)), Resize(770, 137, 790, 157), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

    auto it = deckBuilder.filterList->credit_limits.find(L"genesys");
    if (it != deckBuilder.filterList->credit_limits.end()) {
        // Genesys计分板背景
        driver->draw2DRectangle(Resize(415, 136, 633, 157), 0xff000000, 0xff000000, 0x80000000, 0x80000000);
        // Genesys计分板外框
        driver->draw2DRectangleOutline(Resize(415, 136, 633, 157));

        // 显示“限”的文字图标
        driver->draw2DImage(imageManager.tGSC, Resize_X_Y(420, 137, 440, 156), irr::core::recti(0, 0, 64, 64), 0, 0, true);
        int intValue = static_cast<int>(it->second);//获取被选定的genesys禁卡表的上限值，并显示在界面上
        DrawShadowText(guiFont, std::to_wstring(intValue), Resize_X_Y(445, 137, 465, 156), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true,true);

        //遍历genesys禁卡表的卡片点数表，统计当前卡组点数合计值
        int totalCredits = 0;
        for (auto& card : deckManager.current_deck.main) {
            auto code = (card->second.alias != 0 &&
                         abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) > 0 &&
                         abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) <= 20)
                        ? card->second.alias
                        : card->first;		            auto credit_it = deckBuilder.filterList->credits.find(code);
            if (credit_it != deckBuilder.filterList->credits.end()) {
                for (auto& credit_entry : credit_it->second) {
                    if (credit_entry.first == L"genesys") {
                        totalCredits += credit_entry.second;
                        break;
                    }
                }
            }
        }

        // 统计额外卡组中的点数
        for (auto& card : deckManager.current_deck.extra) {
            auto code = (card->second.alias != 0 &&
                         abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) > 0 &&
                         abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) <= 20)
                        ? card->second.alias
                        : card->first;		            auto credit_it = deckBuilder.filterList->credits.find(code);
            if (credit_it != deckBuilder.filterList->credits.end()) {
                for (auto& credit_entry : credit_it->second) {
                    if (credit_entry.first == L"genesys") {
                        totalCredits += credit_entry.second;
                        break;
                    }
                }
            }
        }
        // 统计副卡组的点数
        for (auto& card : deckManager.current_deck.side) {
            auto code = (card->second.alias != 0 &&
                              abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) > 0 &&
                              abs(static_cast<int>(card->first) - static_cast<int>(card->second.alias)) <= 20)
                             ? card->second.alias
                             : card->first;
            auto credit_it = deckBuilder.filterList->credits.find(code);
            if (credit_it != deckBuilder.filterList->credits.end()) {
                for (auto& credit_entry : credit_it->second) {
                    if (credit_entry.first == L"genesys") {
                        totalCredits += credit_entry.second;
                        break;
                    }
                }
            }
        }
        irr::video::SColor color = 0xffffffff;// 设置默认数字颜色值为白色
        // 显示“计”文字图标和卡组合计点数值
        driver->draw2DImage(imageManager.tGSC, Resize_X_Y(475, 137, 495, 156), irr::core::recti(64, 0, 128, 64), 0, 0, true);
        color = totalCredits > intValue ? 0xffff0000 : 0xffffffff;// 如果点数总和超过上限则设置数字颜色为红色，否则为白色
        DrawShadowText(guiFont, std::to_wstring(totalCredits), Resize_X_Y(500, 137, 520, 156), Resize(0, 1, 2, 0), color, 0xff000000, true,true);

        // 显示“余”文字图标和剩余点数值
        driver->draw2DImage(imageManager.tGSC, Resize_X_Y(530, 137, 550, 156), irr::core::recti(128, 0, 192, 64), 0, 0, true);
        int remaining = intValue - totalCredits;
        color = remaining < 0 ? 0xffff0000 : 0xffffffff;// 剩余点数小于0则设置数字颜色为红色，否则为白色
        DrawShadowText(guiFont, std::to_wstring(remaining), Resize_X_Y(555, 137, 575, 156), Resize(0, 1, 2, 0), color, 0xff000000, true,true);

        // Genesys标志，仅提示这是用于genesys的计分板
        driver->draw2DImage(imageManager.tGSC, Resize(580, 139, 628, 154), irr::core::recti(192, 0, 448, 64), 0, 0, true);
    }

    // 主卡组内容区背景与边框
    driver->draw2DRectangle(Resize(310, 160, 797, deckBuilder.showing_pack ? 630 : 436), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
    driver->draw2DRectangleOutline(Resize(309, 159, 798, deckBuilder.showing_pack ? 630 : 436));

	// 计算主卡组每行显示的列数和间距
	int lx;                      // 每行显示的列数
	int dy = 68;                 // 每行之间的垂直间距，默认为68像素
	float dx;                    // 每列之间的水平间距
	if(mainsize <= 40) {         // 如果主卡组卡片数量小于等于40张
		dx = 436.0f / 9;         // 水平间距为436除以9（10列卡片之间的9个间隙）
		lx = 10;                 // 每行显示10列
	} else if(deckBuilder.showing_pack) {  // 如果正在显示卡包内容
		lx = 10;                 // 默认每行显示10列
		if(mainsize > 10 * 7)    // 如果卡片数量超过70张（10列×7行）
			lx = 11;             // 每行显示11列
		if(mainsize > 11 * 7)    // 如果卡片数量超过77张（11列×7行）
			lx = 12;             // 每行显示12列
		dx = (mainGame->scrPackCards->isVisible() ? 414.0f : 436.0f) / (lx - 1);  // 根据滚动条是否可见计算水平间距
		if(mainsize > 60)        // 如果卡片数量超过60张
			dy = 66;             // 减小垂直间距为66像素，以容纳更多行
	} else {                     // 普通卡组显示模式且卡片数量超过40张
		lx = (mainsize - 41) / 4 + 11;  // 根据超出40张的数量动态计算列数（每增加4张增加1列，最少11列）
		dx = 436.0f / (lx - 1);  // 计算相应的水平间距
	}

	// 根据滚动条位置调整起始索引并绘制主卡组卡片缩略图（多出现在卡包展示中卡组张数超过一页显示时）
	int padding = scrPackCards->getPos() * lx;
	for(int i = 0; i < mainsize - padding && i < 7 * lx; ++i) {
		int j = i + padding;
		DrawThumb(deckManager.current_deck.main[j], Resize(314 + (i % lx) * dx, 164 + (i / lx) * dy), deckBuilder.filterList);
		if(deckBuilder.hovered_pos == 1 && deckBuilder.hovered_seq == j)
			driver->draw2DRectangleOutline(irr::core::recti((313 + (i % lx) * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, (163 + (i / lx) * dy) * yScale, (313 + (i % lx) * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (163 + (i / lx) * dy + CARD_THUMB_HEIGHT + 1) * yScale));
	}

	// 如果不是在卡包展示，则继续绘制额外卡组和副卡组
	if(!deckBuilder.showing_pack) {
		// 额外卡组区域背景与边框
		driver->draw2DRectangle(Resize(310, 440, 410, 460), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 439, 410, 460));

		// 显示“额外卡组”标题
        DrawShadowText(guiFont, dataManager.GetSysString(1331), Resize(310, 439, 395, 459), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);

		// 显示额外卡组数量
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.current_deck.extra.size()), Resize(360, 440, 420, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);

		// 额外卡组内容区背景与边框
		driver->draw2DRectangle(Resize(310, 463, 797, 533), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 462, 798, 533));

		// 额外卡组类型计数区域背景与边框
		driver->draw2DRectangle(Resize(582, 440, 797, 460), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(581, 439, 798, 460));

		// 融合怪兽数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(595, 440, 595+14+3/8, 460), irr::core::recti(0, 32, 23, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_FUSION)), Resize(620, 440, 640, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 同调怪兽数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(645, 440, 645+14+3/8, 460), irr::core::recti(23, 32, 46, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_SYNCHRO)), Resize(670, 440, 690, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// XYZ怪兽数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(695, 440, 695+14+3/8, 460), irr::core::recti(46, 32, 69, 64), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_XYZ)), Resize(720, 440, 740, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 连接怪兽数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(745, 440, 745+14+3/8, 460), irr::core::recti(0, 64, 23, 96), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.extra, TYPE_LINK)), Resize(770, 440, 790, 460), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 绘制额外卡组卡片缩略图
		if(deckManager.current_deck.extra.size() <= 10)
			dx = 436.0f / 9;
		else dx = 436.0f / (deckManager.current_deck.extra.size() - 1);
		for(size_t i = 0; i < deckManager.current_deck.extra.size(); ++i) {
			DrawThumb(deckManager.current_deck.extra[i], Resize(314 + i * dx, 466), deckBuilder.filterList);
			if(deckBuilder.hovered_pos == 2 && deckBuilder.hovered_seq == (int)i)
				driver->draw2DRectangleOutline(irr::core::recti((313 + i * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, 465 * yScale, (313 + i * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (465 + CARD_THUMB_HEIGHT + 1 ) * yScale));
		}

		// 副卡组区域背景与边框
		driver->draw2DRectangle(Resize(310, 537, 410, 557), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 536, 410, 557));

		// 显示“副卡组”标题
        DrawShadowText(guiFont, dataManager.GetSysString(1332), Resize(300, 536, 395, 556), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false, 0);

		// 显示副卡组数量
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.current_deck.side.size()), Resize(360, 537, 420, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 副卡组内容区背景与边框
		driver->draw2DRectangle(Resize(310, 560, 797, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(309, 559, 797, 630));

		// 副卡组类型计数区域背景与边框
		driver->draw2DRectangle(Resize(638, 537, 797, 557), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(637, 536, 798, 557));

		// 副卡组怪兽卡数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(645, 537, 645+14+3/8, 557), irr::core::recti(0, 0, 23, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_MONSTER)), Resize(670, 537, 690, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 副卡组魔法卡数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(695, 537, 695+14+3/8, 557), irr::core::recti(23, 0, 46, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_SPELL)), Resize(720, 537, 740, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 副卡组陷阱卡数量图标与文本
		driver->draw2DImage(imageManager.tCardType, Resize(745, 537, 745+14+3/8, 557), irr::core::recti(46, 0, 69, 32), 0, 0, true);
        DrawShadowText(numFont, dataManager.GetNumString(deckManager.TypeCount(deckManager.current_deck.side, TYPE_TRAP)), Resize(770, 537, 790, 557), Resize(0, 1, 2, 0), 0xffffffff, 0xff000000, true, false);

		// 绘制副卡组卡片缩略图
		if(deckManager.current_deck.side.size() <= 10)
			dx = 436.0f / 9;
		else dx = 436.0f / (deckManager.current_deck.side.size() - 1);
		for(size_t i = 0; i < deckManager.current_deck.side.size(); ++i) {
			DrawThumb(deckManager.current_deck.side[i], Resize(314 + i * dx, 564), deckBuilder.filterList);
			if(deckBuilder.hovered_pos == 3 && deckBuilder.hovered_seq == (int)i)
				driver->draw2DRectangleOutline(irr::core::recti((313 + i * dx) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale - mainGame->yScale) / 2, 563 * yScale, (313 + i * dx + 1) * xScale + CARD_THUMB_WIDTH * (mainGame->xScale + mainGame->yScale) / 2, (563 + CARD_THUMB_HEIGHT + 1) * yScale));
		}
	}

	// 判断是否处于match局内换副卡组的时候，是则绘制相应区域
	if(is_siding) {
		// 将换副卡组时的聊天内容显示到原搜索结果区域
        //换副卡组时聊天内容的背景外框和背景色
		driver->draw2DRectangle(Resize(806, 10, 1020, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 9, 1020, 630));
	} else {
		// 搜索结果区域背景与边框
		driver->draw2DRectangle(Resize(806, 137, 930,157), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 136, 930,157));

		// 显示“搜索结果”标题
        DrawShadowText(guiFont, dataManager.GetSysString(1333),Resize(810, 136, 875,156),Resize(0, 1, 2, 0), 0xffffffff,0xff000000,false, false);

		// 显示搜索结果数量
        DrawShadowText(numFont, deckBuilder.result_string,Resize(880, 136, 930,156),Resize(0, 1, 2, 0), 0xffffffff,0xff000000,false, false);

		// 搜索结果内容区背景与边框
		driver->draw2DRectangle(Resize(806, 160, 1020, 630), 0x400000ff, 0x400000ff, 0x40000000, 0x40000000);
		driver->draw2DRectangleOutline(Resize(805, 159, 1020, 630));
	}

	// 绘制搜索结果列表项
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

		// 根据卡片类型分别处理显示逻辑
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
			const auto& attribute = dataManager.FormatAttribute(ptr->second.attribute);
			const auto& race = dataManager.FormatRace(ptr->second.race);
			myswprintf(textBuffer, L"%ls/%ls %ls%d", attribute.c_str(), race.c_str(), form, ptr->second.level);
            DrawShadowText(guiFont, textBuffer, Resize(850, 186 + i * 66, 1000, 207 + i * 66), Resize(1, 1, 0, 0));
			if(ptr->second.type & TYPE_PENDULUM) {
				myswprintf(scaleBuffer, L" %d/%d", ptr->second.lscale, ptr->second.rscale);
			}
			myswprintf(textBuffer, L"%ls%ls%ls", adBuffer, scaleBuffer, availBuffer);
            DrawShadowText(guiFont, textBuffer, Resize(850, 209 + i * 66, 1000, 230 + i * 66), Resize(1, 1, 0, 0));
		} else {
			myswprintf(textBuffer, L"%ls", dataManager.GetName(ptr->first));
            DrawShadowText(guiFont, textBuffer, Resize(850, 164 + i * 66, 1000, 185 + i * 66), Resize(1, 1, 0, 0));
			const auto& type = dataManager.FormatType(ptr->second.type);
			myswprintf(textBuffer, L"%ls", type.c_str());
            DrawShadowText(guiFont, textBuffer, Resize(850, 186 + i * 66, 1000, 207 + i * 66), Resize(1, 1, 0, 0));
			myswprintf(textBuffer, L"%ls", availBuffer);
            DrawShadowText(textFont, textBuffer, Resize(850, 209 + i * 66, 1000, 230 + i * 66), Resize(1, 1 , 0, 0));
		}
	}

	// 如果正在拖拽卡片，则绘制拖拽中的卡片缩略图
	if(deckBuilder.is_draging) {
		DrawThumb(deckBuilder.draging_pointer, irr::core::vector2di(deckBuilder.dragx - CARD_THUMB_WIDTH / 2 * mainGame->xScale, deckBuilder.dragy - CARD_THUMB_HEIGHT / 2 * mainGame->yScale), deckBuilder.filterList, true);
	}
}
/**
 * @brief 绘制表情包
 */
void Game::DrawEmoticon() {
    static int emoticonShowTime = 120;// 表情显示计时（改为静态变量）

    if(showingEmoticon) {
        emoticonShowTime--;
        if(emoticonShowTime <= 0) {
            emoticonShowTime = 120; // 重置计时器
            showingEmoticon = false;
        }
    }
    if(!showingEmoticon) {
        return;
    }

    // 根据发送者决定显示位置
    irr::core::recti emoticonRect = isMyEmoticon ? Resize_X_Y(335, 90, 390, 145) : Resize_X_Y(930, 90, 985, 145);
    // 绘制表情图片
    driver->draw2DImage(imageManager.tEmoticons, emoticonRect, imageManager.emoticonRects[currentEmoticonCode], nullptr,nullptr,true);
    // 根据发送者决定显示位置
    irr::core::recti BubbleHeptagonBorderRect = isMyEmoticon ? Resize_X_Y(335, 80, 390, 150) : Resize_X_Y(930, 80, 985, 150);
    // 绘制对话气泡形状的7边形外框
    DrawBubbleHeptagonBorder(BubbleHeptagonBorderRect, 0xffffffff, 2);
}

/**
 * @brief 绘制对话气泡形状的7边形外框
 * 由正方形底部和顶部三角形组成
 */
void Game::DrawBubbleHeptagonBorder(const irr::core::recti& rect, irr::video::SColor color, int borderWidth) const {
    // 计算中心点和尺寸
    float centerX = (rect.UpperLeftCorner.X + rect.LowerRightCorner.X) / 2.0f;
    float centerY = (rect.UpperLeftCorner.Y + rect.LowerRightCorner.Y) / 2.0f;
    float width = rect.getWidth();
    float height = rect.getHeight();

    // 定义7边形的7个顶点（按顺时针方向）- 使用整数类型
    irr::core::position2d<irr::s32> vertices[7];

    // 1. 左下角
    vertices[0] = irr::core::position2d<irr::s32>(rect.UpperLeftCorner.X - static_cast<irr::s32>(height * 0.1f), rect.LowerRightCorner.Y);
    // 2. 右下角
    vertices[1] = irr::core::position2d<irr::s32>(rect.LowerRightCorner.X + static_cast<irr::s32>(height * 0.1f), rect.LowerRightCorner.Y);
    // 3. 右上角（接近三角形起点）
    vertices[2] = irr::core::position2d<irr::s32>(rect.LowerRightCorner.X + static_cast<irr::s32>(height * 0.1f), rect.UpperLeftCorner.Y + static_cast<irr::s32>(height * 0.1f));
    // 4. 三角形右顶点
    vertices[3] = irr::core::position2d<irr::s32>(static_cast<irr::s32>(centerX + width * 0.15f), rect.UpperLeftCorner.Y + static_cast<irr::s32>(height * 0.1f));
    // 5. 三角形顶点
    vertices[4] = irr::core::position2d<irr::s32>(static_cast<irr::s32>(centerX), rect.UpperLeftCorner.Y - static_cast<irr::s32>(height * 0.1f)); // 三角形尖端稍微超出矩形
    // 6. 三角形左顶点
    vertices[5] = irr::core::position2d<irr::s32>(static_cast<irr::s32>(centerX - width * 0.15f), rect.UpperLeftCorner.Y + static_cast<irr::s32>(height * 0.1f));
    // 7. 左上角（接近三角形起点）
    vertices[6] = irr::core::position2d<irr::s32>(rect.UpperLeftCorner.X - static_cast<irr::s32>(height * 0.1f), rect.UpperLeftCorner.Y + static_cast<irr::s32>(height * 0.1f));

    // 绘制7条边
    for(int i = 0; i < 7; i++) {
        int nextIndex = (i + 1) % 7;
        driver->draw2DLine(vertices[i], vertices[nextIndex], color);
    }
}

}
