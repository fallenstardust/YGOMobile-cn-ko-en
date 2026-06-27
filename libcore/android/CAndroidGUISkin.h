/*
 * CAndroidGUISkin.h
 *
 *  Created on: 2014-11-5
 *      Author: mabin
 */

#ifndef CANDROIDGUISKIN_H_
#define CANDROIDGUISKIN_H_

// 包含必要的Irrlicht图形用户界面头文件
#include <IGUISkin.h>
#include <irrString.h>
#include <IrrCompileConfig.h>
#include <IVideoDriver.h>
#include <IGUIEnvironment.h>

namespace irr {
namespace gui {

// 定义Android GUI皮肤类，继承自IGUISkin接口
class CAndroidGUISkin: public IGUISkin {
public:
	// 构造函数，初始化皮肤类型、视频驱动和缩放因子
	CAndroidGUISkin(EGUI_SKIN_TYPE type, video::IVideoDriver* driver, float x, float y);

	// 静态工厂方法，创建Android皮肤实例
	static CAndroidGUISkin* createAndroidSkin(EGUI_SKIN_TYPE type, video::IVideoDriver* driver, IGUIEnvironment* env, float x, float y);

	// 析构函数
	virtual ~CAndroidGUISkin();

	// 获取默认颜色值
	virtual video::SColor getColor(EGUI_DEFAULT_COLOR color) const _IRR_OVERRIDE_;

	// 设置指定类型的默认颜色
	virtual void setColor(EGUI_DEFAULT_COLOR which, video::SColor newColor) _IRR_OVERRIDE_;

	// 获取指定类型的尺寸大小
	virtual s32 getSize(EGUI_DEFAULT_SIZE size) const _IRR_OVERRIDE_;

	// 设置指定类型的尺寸大小
	virtual void setSize(EGUI_DEFAULT_SIZE which, s32 size) _IRR_OVERRIDE_;

	// 获取默认字体
	virtual IGUIFont* getFont(EGUI_DEFAULT_FONT which=EGDF_DEFAULT) const _IRR_OVERRIDE_;

	// 设置默认字体
	virtual void setFont(IGUIFont* font, EGUI_DEFAULT_FONT which=EGDF_DEFAULT) _IRR_OVERRIDE_;

	// 设置用于绘制图标的SpriteBank
	virtual void setSpriteBank(IGUISpriteBank* bank) _IRR_OVERRIDE_;

	// 获取用于绘制图标的SpriteBank
	virtual IGUISpriteBank* getSpriteBank() const _IRR_OVERRIDE_;

	// 返回默认图标索引
	virtual u32 getIcon(EGUI_DEFAULT_ICON icon) const _IRR_OVERRIDE_;

	// 设置默认图标
	virtual void setIcon(EGUI_DEFAULT_ICON icon, u32 index) _IRR_OVERRIDE_;

	// 返回默认文本（如消息框按钮标题："确定"、"取消"等）
	virtual const wchar_t* getDefaultText(EGUI_DEFAULT_TEXT text) const _IRR_OVERRIDE_;

	// 设置默认文本
	virtual void setDefaultText(EGUI_DEFAULT_TEXT which, const wchar_t* newText) _IRR_OVERRIDE_;

	// 绘制标准3D按钮面板
	virtual void draw3DButtonPaneStandard(IGUIElement* element,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制按下状态的3D按钮面板
	virtual void draw3DButtonPanePressed(IGUIElement* element,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制凹陷的3D面板（用于编辑框、组合框或复选框背景）
	virtual void draw3DSunkenPane(IGUIElement* element,
			video::SColor bgcolor, bool flat,
			bool fillBackGround,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制窗口背景
	virtual core::rect<s32> draw3DWindowBackground(IGUIElement* element,
			bool drawTitleBar, video::SColor titleBarColor,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip,
			core::rect<s32>* checkClientArea) _IRR_OVERRIDE_;

	// 绘制标准3D菜单面板
	virtual void draw3DMenuPane(IGUIElement* element,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制标准3D工具栏
	virtual void draw3DToolBar(IGUIElement* element,
			const core::rect<s32>& rect,
			const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制标签按钮
	virtual void draw3DTabButton(IGUIElement* element, bool active,
		const core::rect<s32>& rect, const core::rect<s32>* clip=0,
		EGUI_ALIGNMENT alignment=EGUIA_UPPERLEFT) _IRR_OVERRIDE_;

	// 绘制标签控制主体
	virtual void draw3DTabBody(IGUIElement* element, bool border, bool background,
		const core::rect<s32>& rect, const core::rect<s32>* clip=0, s32 tabHeight=-1,
		EGUI_ALIGNMENT alignment=EGUIA_UPPERLEFT) _IRR_OVERRIDE_;

	// 绘制图标（通常来自皮肤的SpriteBank）
	virtual void drawIcon(IGUIElement* element, EGUI_DEFAULT_ICON icon,
			const core::position2di position,
			u32 starttime=0, u32 currenttime=0,
			bool loop=false, const core::rect<s32>* clip=0) _IRR_OVERRIDE_;

	// 绘制2D矩形
	virtual void draw2DRectangle(IGUIElement* element, const video::SColor &color,
			const core::rect<s32>& pos, const core::rect<s32>* clip = 0) _IRR_OVERRIDE_;

	// 获取此皮肤的类型
	virtual EGUI_SKIN_TYPE getType() const _IRR_OVERRIDE_;

	// 写入皮肤属性
	virtual void serializeAttributes(io::IAttributes* out, io::SAttributeReadWriteOptions* options=0) const _IRR_OVERRIDE_;

	// 读取皮肤属性
	virtual void deserializeAttributes(io::IAttributes* in, io::SAttributeReadWriteOptions* options=0) _IRR_OVERRIDE_;

private:
	// 私有成员变量：存储各种GUI元素的颜色、尺寸、图标、字体等属性
	video::SColor Colors[EGDC_COUNT];           // 颜色数组
	s32 Sizes[EGDS_COUNT];                     // 尺寸数组
	u32 Icons[EGDI_COUNT];                     // 图标索引数组
	IGUIFont* Fonts[EGDF_COUNT];               // 字体指针数组
	IGUISpriteBank* SpriteBank;                // SpriteBank指针
	core::stringw Texts[EGDT_COUNT];           // 文本字符串数组
	irr::video::IVideoDriver* Driver;          // 视频驱动指针
	bool UseGradient;                          // 是否使用渐变效果标志

	EGUI_SKIN_TYPE Type;                       // 皮肤类型

	float XScale;                              // X轴缩放因子
	float YScale;                              // Y轴缩放因子
};

} /* namespace gui */
} /* namespace irr */
#endif /* CANDROIDGUISKIN_H_ */
