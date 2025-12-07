#ifdef _MSC_VER
#pragma warning(disable: 4244)
#endif

#include "CGUIImageButton.h"
#if defined(_IRR_ANDROID_PLATFORM_)
#include "game.h"
#endif

namespace irr {
namespace gui {

/**
 * @brief 在2D空间中绘制一个带有旋转、缩放功能的图像。
 *
 * 此函数使用 Irrlicht 引擎提供的接口，在屏幕上以指定的位置、旋转中心点、角度和缩放比例来渲染一张纹理图片。
 * 支持透明通道以及自定义颜色混合，并能适配不同平台（如 Android）下的材质类型设置。
 *
 * @param driver 视频驱动指针，用于执行实际绘图操作。
 * @param image 要绘制的纹理资源。
 * @param sourceRect 源图像中的矩形区域，表示要从纹理中裁剪出的部分。
 * @param position 图像在屏幕上的起始绘制位置（左上角坐标）。
 * @param rotationPoint 图像绕其旋转的中心点（相对于 position 的偏移量）。
 * @param rotation 顺时针旋转的角度（单位：度）。
 * @param scale 缩放因子，分别控制 X 和 Y 方向的拉伸程度。
 * @param useAlphaChannel 是否启用 Alpha 透明通道进行混合。
 * @param color 应用于整个图像的颜色调制（包括透明度）。
 */
void Draw2DImageRotation(video::IVideoDriver* driver, video::ITexture* image, core::rect<s32> sourceRect,
                         core::vector2d<s32> position, core::vector2d<s32> rotationPoint, f32 rotation, core::vector2df scale, bool useAlphaChannel, video::SColor color) {
	// 保存并重置投影矩阵与视图矩阵，以便直接使用屏幕坐标进行绘制
	irr::video::SMaterial material;
	irr::core::matrix4 oldProjMat = driver->getTransform(irr::video::ETS_PROJECTION);
	driver->setTransform(irr::video::ETS_PROJECTION, irr::core::matrix4());
	irr::core::matrix4 oldViewMat = driver->getTransform(irr::video::ETS_VIEW);
	driver->setTransform(irr::video::ETS_VIEW, irr::core::matrix4());

	// 计算图像四个顶点的初始位置（未旋转前）
	irr::core::vector2df corner[4];
	corner[0] = irr::core::vector2df(position.X, position.Y);
	corner[1] = irr::core::vector2df(position.X + sourceRect.getWidth() * scale.X, position.Y);
	corner[2] = irr::core::vector2df(position.X, position.Y + sourceRect.getHeight() * scale.Y);
	corner[3] = irr::core::vector2df(position.X + sourceRect.getWidth() * scale.X, position.Y + sourceRect.getHeight() * scale.Y);

	// 若有旋转，则根据给定的旋转中心点对所有顶点应用旋转变换
	if (rotation != 0.0f) {
		for (int x = 0; x < 4; x++)
			corner[x].rotateBy(rotation, irr::core::vector2df(rotationPoint.X, rotationPoint.Y));
	}

	// 提取源纹理坐标的 UV 值，并将其归一化到 [0,1] 区间内
	irr::core::vector2df uvCorner[4];
	uvCorner[0] = irr::core::vector2df(sourceRect.UpperLeftCorner.X, sourceRect.UpperLeftCorner.Y);
	uvCorner[1] = irr::core::vector2df(sourceRect.LowerRightCorner.X, sourceRect.UpperLeftCorner.Y);
	uvCorner[2] = irr::core::vector2df(sourceRect.UpperLeftCorner.X, sourceRect.LowerRightCorner.Y);
	uvCorner[3] = irr::core::vector2df(sourceRect.LowerRightCorner.X, sourceRect.LowerRightCorner.Y);
	for (int x = 0; x < 4; x++) {
		float uvX = uvCorner[x].X / (float)image->getOriginalSize().Width;
		float uvY = uvCorner[x].Y / (float)image->getOriginalSize().Height;
		uvCorner[x] = irr::core::vector2df(uvX, uvY);
	}

	// 构造最终传入 GPU 绘制的顶点数据结构（包含位置、UV 和颜色信息）
	irr::video::S3DVertex vertices[4];
	irr::u16 indices[6] = { 0, 1, 2, 3, 2, 1 };
	float screenWidth = driver->getScreenSize().Width;
	float screenHeight = driver->getScreenSize().Height;
	for (int x = 0; x < 4; x++) {
		float screenPosX = ((corner[x].X / screenWidth) - 0.5f) * 2.0f;
		float screenPosY = ((corner[x].Y / screenHeight) - 0.5f) * -2.0f;
		vertices[x].Pos = irr::core::vector3df(screenPosX, screenPosY, 1);
		vertices[x].TCoords = uvCorner[x];
		vertices[x].Color = color;
	}

	// 设置材质属性，关闭光照和深度写入，绑定纹理及选择合适的材质类型（支持跨平台）
	material.Lighting = false;
	material.ZWriteEnable = false;
	material.TextureLayer[0].Texture = image;
#if defined(_IRR_ANDROID_PLATFORM_)
	if (!ygo::mainGame->isNPOTSupported) {
		material.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
		material.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
	}
	if (useAlphaChannel)
			material.MaterialType = (video::E_MATERIAL_TYPE)ygo::mainGame->ogles2TrasparentAlpha;
		else material.MaterialType = (video::E_MATERIAL_TYPE)ygo::mainGame->ogles2Solid;
#else
	if (useAlphaChannel)
		material.MaterialType = irr::video::EMT_TRANSPARENT_ALPHA_CHANNEL;
	else material.MaterialType = irr::video::EMT_SOLID;
#endif

	// 执行绘制命令并将变换矩阵恢复原状
	driver->setMaterial(material);
	driver->drawIndexedTriangleList(&vertices[0], 4, &indices[0], 2);
	driver->setTransform(irr::video::ETS_PROJECTION, oldProjMat);
	driver->setTransform(irr::video::ETS_VIEW, oldViewMat);
}

/**
 * @brief 在2D空间中绘制一个图像四边形（quad），支持指定纹理区域、顶点坐标、透明度及颜色。
 *
 * 此函数使用 Irrlicht 引擎提供的接口，在屏幕空间直接绘制一张纹理图片的一部分，
 * 形成由四个角定义的任意四边形。适用于 UI 元素或特殊效果渲染。
 *
 * @param driver 视频驱动指针，用于执行绘图操作。
 * @param image 要绘制的纹理资源。
 * @param sourceRect 指定从纹理中裁剪出的矩形区域（像素单位）。
 * @param corner 四个二维向量组成的数组，表示在屏幕上的四个角的位置（像素单位）。
 * @param useAlphaChannel 是否启用 Alpha 通道进行透明混合。
 * @param color 应用于整个 quad 的顶点颜色（包括 alpha 值）。
 */
void Draw2DImageQuad(video::IVideoDriver* driver, video::ITexture* image, core::rect<s32> sourceRect,
                     core::vector2d<s32> corner[4], bool useAlphaChannel, video::SColor color) {

	// 初始化材质并保存当前投影和视图矩阵
	irr::video::SMaterial material;
	irr::core::matrix4 oldProjMat = driver->getTransform(irr::video::ETS_PROJECTION);
	driver->setTransform(irr::video::ETS_PROJECTION, irr::core::matrix4());
	irr::core::matrix4 oldViewMat = driver->getTransform(irr::video::ETS_VIEW);
	driver->setTransform(irr::video::ETS_VIEW, irr::core::matrix4());

	// 计算 UV 坐标：将像素级纹理坐标转换为 [0,1] 范围内的归一化坐标
	irr::core::vector2df uvCorner[4];
	uvCorner[0] = irr::core::vector2df(sourceRect.UpperLeftCorner.X, sourceRect.UpperLeftCorner.Y);
	uvCorner[1] = irr::core::vector2df(sourceRect.LowerRightCorner.X, sourceRect.UpperLeftCorner.Y);
	uvCorner[2] = irr::core::vector2df(sourceRect.UpperLeftCorner.X, sourceRect.LowerRightCorner.Y);
	uvCorner[3] = irr::core::vector2df(sourceRect.LowerRightCorner.X, sourceRect.LowerRightCorner.Y);

	for (int x = 0; x < 4; x++) {
		float uvX = uvCorner[x].X / (float)image->getOriginalSize().Width;
		float uvY = uvCorner[x].Y / (float)image->getOriginalSize().Height;
		uvCorner[x] = irr::core::vector2df(uvX, uvY);
	}

	// 构造顶点数据：位置映射到 [-1, 1] 屏幕坐标系，并设置 UV 和颜色
	irr::video::S3DVertex vertices[4];
	irr::u16 indices[6] = { 0, 1, 2, 3, 2, 1 };
	float screenWidth = driver->getScreenSize().Width;
	float screenHeight = driver->getScreenSize().Height;

	for (int x = 0; x < 4; x++) {
		float screenPosX = ((corner[x].X / screenWidth) - 0.5f) * 2.0f;
		float screenPosY = ((corner[x].Y / screenHeight) - 0.5f) * -2.0f;
		vertices[x].Pos = irr::core::vector3df(screenPosX, screenPosY, 1);
		vertices[x].TCoords = uvCorner[x];
		vertices[x].Color = color;
	}

	// 设置材质属性：禁用光照与深度写入，绑定纹理
	material.Lighting = false;
	material.ZWriteEnable = false;
	material.TextureLayer[0].Texture = image;

#if defined(_IRR_ANDROID_PLATFORM_)
	// Android 平台下根据 NPOT 支持情况调整纹理环绕方式
	if (!ygo::mainGame->isNPOTSupported) {
		material.TextureLayer[0].TextureWrapU = irr::video::ETC_CLAMP_TO_EDGE;
		material.TextureLayer[0].TextureWrapV = irr::video::ETC_CLAMP_TO_EDGE;
	}
	// 根据是否使用透明通道选择对应的材质类型（Android GLES 特有）
	if (useAlphaChannel)
		material.MaterialType = (video::E_MATERIAL_TYPE)ygo::mainGame->ogles2TrasparentAlpha;
	else
		material.MaterialType = (video::E_MATERIAL_TYPE)ygo::mainGame->ogles2Solid;
#else
	// 非 Android 平台使用标准材质类型
	if (useAlphaChannel)
		material.MaterialType = irr::video::EMT_TRANSPARENT_ALPHA_CHANNEL;
	else
		material.MaterialType = irr::video::EMT_SOLID;
#endif

	// 应用材质并绘制三角形列表（两个三角形组成一个 quad）
	driver->setMaterial(material);
	driver->drawIndexedTriangleList(&vertices[0], 4, &indices[0], 2);

	// 恢复原始的投影和视图变换矩阵
	driver->setTransform(irr::video::ETS_PROJECTION, oldProjMat);
	driver->setTransform(irr::video::ETS_VIEW, oldViewMat);
}

/**
 * @brief CGUIImageButton类的构造函数
 * @param environment GUI环境指针，用于创建和管理GUI元素
 * @param parent 父级GUI元素指针，新创建的按钮将作为其子元素
 * @param id 控件ID，用于标识和区分不同的GUI元素
 * @param rectangle 按钮的矩形区域，定义了按钮在屏幕上的位置和大小
 */
CGUIImageButton::CGUIImageButton(IGUIEnvironment* environment, IGUIElement* parent, s32 id, core::rect<s32> rectangle)
	: CGUIButton(environment, parent, id, rectangle) {
	// 初始化图像按钮的默认属性
	isDrawImage = true;
	isFixedSize = false;
	imageRotation = 0.0f;
	imageScale = core::vector2df(1.0f, 1.0f);
	imageSize = core::dimension2di(rectangle.getWidth(), rectangle.getHeight());
}

/**
 * 创建并添加一个图像按钮到GUI环境
 * @param env GUI环境指针，用于创建和管理GUI元素
 * @param rectangle 按钮的位置和大小矩形区域
 * @param parent 父级GUI元素，如果为NULL则使用环境的根元素
 * @param id 按钮的唯一标识符
 * @return 返回新创建的图像按钮指针
 */
CGUIImageButton* CGUIImageButton::addImageButton(IGUIEnvironment *env, const core::rect<s32>& rectangle, IGUIElement* parent, s32 id) {
	// 创建新的图像按钮实例
	CGUIImageButton* button = new CGUIImageButton(env, parent ? parent : 0, id, rectangle);
	// 释放引用计数，避免内存泄漏
	button->drop();
	return button;
}

/**
 * @brief 绘制图像按钮
 *
 * 此函数负责绘制CGUIImageButton控件，包括按钮的边框、图像以及按下状态的视觉效果。
 * 根据按钮是否被按下，会调整图像位置并绘制相应的3D按钮面板。
 *
 * @note 该函数重写了基类的draw方法，实现了自定义的图像按钮绘制逻辑
 */
void CGUIImageButton::draw() {
	// 检查控件是否可见，不可见则直接返回
	if (!IsVisible)
		return;

	// 获取GUI皮肤和视频驱动器实例
	IGUISkin* skin = Environment->getSkin();
	video::IVideoDriver* driver = Environment->getVideoDriver();

	// 计算按钮绝对矩形区域的中心点
	irr::core::vector2di center = AbsoluteRect.getCenter();

	// 计算图像绘制的起始位置，使其居中显示
	irr::core::vector2di pos = center;
	pos.X -= (s32)(ImageRect.getWidth() * imageScale.X * 0.5f);
	pos.Y -= (s32)(ImageRect.getHeight() * imageScale.Y * 0.5f);

	// 处理按钮按下状态的视觉效果
	if(Pressed) {
		// 按下时将图像位置和中心点向下右偏移1个像素
		pos.X += 1;
		pos.Y += 1;
		center.X += 1;
		center.Y += 1;

		// 如果需要绘制边框，则绘制按下状态的3D按钮面板
		if (DrawBorder)
			skin->draw3DButtonPanePressed(this, AbsoluteRect, &AbsoluteClippingRect);
	} else {
		// 如果需要绘制边框，则绘制标准状态的3D按钮面板
		if (DrawBorder)
			skin->draw3DButtonPaneStandard(this, AbsoluteRect, &AbsoluteClippingRect);
	}

	// 如果存在图像且允许绘制图像，则执行图像绘制操作
	if(Image && isDrawImage)
		irr::gui::Draw2DImageRotation(driver, Image, ImageRect, pos, center, imageRotation, imageScale);

	// 调用基类的绘制方法完成剩余的绘制工作
	IGUIElement::draw();
}

/*!
\brief 设置按钮的图像
\param image 指向要设置的纹理图像的指针
\return 无返回值
*/
void CGUIImageButton::setImage(video::ITexture* image)
{
	// 增加新图像的引用计数
	if(image)
		image->grab();

	// 减少旧图像的引用计数并释放资源
	if(Image)
		Image->drop();

	// 更新图像指针
	Image = image;

	// 如果新图像存在，更新图像矩形区域和缩放比例
	if(image) {
		ImageRect = core::rect<s32>(core::vector2d<s32>(0, 0), image->getOriginalSize());
		if(isFixedSize)
			imageScale = core::vector2df((irr::f32)imageSize.Width / image->getSize().Width, (irr::f32)imageSize.Height / image->getSize().Height);
	}

	// 如果没有设置按下状态的图像，则使用当前图像作为按下状态图像
	if(!PressedImage)
		setPressedImage(Image);
}

void CGUIImageButton::setDrawImage(bool b) {
	isDrawImage = b;
}
void CGUIImageButton::setImageRotation(f32 r) {
	imageRotation = r;
}
void CGUIImageButton::setImageScale(core::vector2df s) {
	imageScale = s;
}
void CGUIImageButton::setImageSize(core::dimension2di s) {
	isFixedSize = true;
	imageSize = s;
}

IGUIFont* CGUIImageButton::getOverrideFont( void ) const
{
	IGUISkin* skin = Environment->getSkin();
	if (!skin)
		return nullptr;
	return skin->getFont();
}

IGUIFont* CGUIImageButton::getActiveFont() const
{
	IGUISkin* skin = Environment->getSkin();
	if (!skin)
		return nullptr;
	return skin->getFont();
}

}
}
