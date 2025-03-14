package com.reco1l.andengine

import com.reco1l.framework.math.Vec2
import com.reco1l.framework.math.Vec4
import org.anddev.andengine.entity.IEntity
import org.anddev.andengine.entity.scene.CameraScene
import org.anddev.andengine.entity.shape.IShape


fun IEntity?.getPadding() = when (this) {
    is ExtendedEntity -> padding
    else -> Vec4.Zero
}

fun IEntity?.getPaddedWidth() = when (this) {
    is ExtendedEntity -> drawWidth - padding.horizontal
    is CameraScene -> camera.widthRaw
    is IShape -> width
    else -> 0f
}

fun IEntity?.getPaddedHeight() = when (this) {
    is ExtendedEntity -> drawHeight - padding.vertical
    is CameraScene -> camera.heightRaw
    is IShape -> height
    else -> 0f
}


/**
 * The size of the entity.
 *
 * When using the getter this will return the maximum value between the width and height or the same.
 * When using the setter this will set the width and height to the same value.
 */
var ExtendedEntity.size
    get() = Vec2(width, height)
    set(value) {
        setSize(value.x, value.y)
    }

/**
 * The draw size of the entity.
 */
val ExtendedEntity.drawSize
    get() = Vec2(drawWidth, drawHeight)

/**
 * The position of the entity.
 */
var ExtendedEntity.position
    get() = Vec2(x, y)
    set(value) {
        setPosition(value.x, value.y)
    }

/**
 * The draw position of the entity.
 */
val ExtendedEntity.drawPosition
    get() = Vec2(drawX, drawY)


/**
 * The scale of the entity.
 */
var ExtendedEntity.scale
    get() = Vec2(scaleX, scaleY)
    set(value) {
        setScale(value.x, value.y)
    }

/**
 * The center where the entity will scale from.
 */
var ExtendedEntity.scaleCenter
    get() = Vec2(scaleCenterX, scaleCenterY)
    set(value) {
        setScaleCenter(value.x, value.y)
    }

/**
 * The center where the entity will rotate from.
 */
var ExtendedEntity.rotationCenter
    get() = Vec2(rotationCenterX, rotationCenterY)
    set(value) {
        setRotationCenter(value.x, value.y)
    }


/**
 * The total offset applied to the entity.
 */
val ExtendedEntity.totalOffset
    get() = Vec2(totalOffsetX, totalOffsetY)

/**
 * The total offset applied to the X axis.
 */
val ExtendedEntity.totalOffsetX
    get() = originOffsetX + anchorOffsetX + translationX

/**
 * The total offset applied to the Y axis.
 */
val ExtendedEntity.totalOffsetY
    get() = originOffsetY + anchorOffsetY + translationY


/**
 * The offset applied to the entity according to the anchor factor.
 */
val ExtendedEntity.anchorOffset
    get() = Vec2(anchorOffsetX, anchorOffsetY)

/**
 * The offset applied to the X axis according to the anchor factor.
 */
val ExtendedEntity.anchorOffsetX: Float
    get() = parent.getPaddedWidth() * anchor.x

/**
 * The offset applied to the Y axis according to the anchor factor.
 */
val ExtendedEntity.anchorOffsetY: Float
    get() = parent.getPaddedHeight() * anchor.y


/**
 * The offset applied to the entity according to the origin factor.
 */
val ExtendedEntity.originOffset
    get() = Vec2(originOffsetX, originOffsetY)

/**
 * The offset applied to the X axis according to the origin factor.
 */
val ExtendedEntity.originOffsetX: Float
    get() = -(drawWidth * origin.x)

/**
 * The offset applied to the Y axis according to the origin factor.
 */
val ExtendedEntity.originOffsetY: Float
    get() = -(drawHeight * origin.y)


/**
 * Returns the draw width of the entity.
 */
fun IEntity?.getDrawWidth(): Float = when (this) {
    is ExtendedEntity -> drawWidth
    is IShape -> width
    else -> 0f
}

/**
 * Returns the draw height of the entity.
 */
fun IEntity?.getDrawHeight(): Float = when (this) {
    is ExtendedEntity -> drawHeight
    is IShape -> height
    else -> 0f
}

/**
 * Returns the draw X position of the entity.
 */
fun IEntity?.getDrawX(): Float = when (this) {
    is ExtendedEntity -> drawX
    is IShape -> x
    else -> 0f
}

/**
 * Returns the draw Y position of the entity.
 */
fun IEntity?.getDrawY(): Float = when (this) {
    is ExtendedEntity -> drawY
    is IShape -> y
    else -> 0f
}
