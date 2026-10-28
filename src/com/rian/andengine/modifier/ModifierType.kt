package com.rian.andengine.modifier

/**
 * The type of the [UniversalModifier].
 *
 * @author Reco1I, Rian8337
 */
enum class ModifierType(
    /**
     * The target member of this [ModifierType].
     *
     * This is used to group [ModifierType]s that operate on the same property (or properties) of a [ModifierTarget]. It is
     * assumed that [UniversalModifier]s with different [targetMember]s are independent of each other in that they
     * affect different properties, and therefore they can be applied independently in any order without affecting the
     * end result.
     */
    val targetMember: String?
) {
    None(null),

    /**
     * Modifies the [ModifierTarget]'s X scale value.
     */
    ScaleX("scale"),

    /**
     * Modifies the [ModifierTarget]'s Y scale value.
     */
    ScaleY("scale"),

    /**
     * Modifies the [ModifierTarget]'s X and Y scale values.
     */
    ScaleXY("scale"),

    /**
     * Modifies the [ModifierTarget]'s alpha value.
     */
    Alpha("alpha"),

    /**
     * Modifies the [ModifierTarget]'s color value.
     */
    Color("color"),

    /**
     * Modifies the [ModifierTarget]'s X position.
     */
    MoveX("move"),

    /**
     * Modifies the [ModifierTarget]'s Y position.
     */
    MoveY("move"),

    /**
     * Modifies the [ModifierTarget]'s X and Y position.
     */
    MoveXY("move"),

    /**
     * Modifies the [ModifierTarget]'s X translation.
     */
    TranslateX("translate"),

    /**
     * Modifies the [ModifierTarget]'s Y translation.
     */
    TranslateY("translate"),

    /**
     * Modifies the [ModifierTarget]'s X and Y translation.
     */
    TranslateXY("translate"),

    /**
     * Modifies the [ModifierTarget]'s rotation.
     */
    Rotation("rotation"),

    /**
     * Modifies the [ModifierTarget]'s width.
     */
    Width("size"),

    /**
     * Modifies the [ModifierTarget]'s height.
     */
    Height("size"),

    /**
     * Modifies the [ModifierTarget]'s width and height.
     */
    Size("size");

    fun getInitialValues(entity: ModifierTarget, reusableArray: FloatArray) = when (this) {
        ScaleX -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getScaleX()
            array
        }

        ScaleY -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getScaleY()
            array
        }

        ScaleXY -> {
            val array = reusableArray.ensureSize(2)

            array[0] = entity.getScaleX()
            array[1] = entity.getScaleY()
            array
        }

        Alpha -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getAlpha()
            array
        }

        Color -> {
            val array = reusableArray.ensureSize(3)

            array[0] = entity.getRed()
            array[1] = entity.getGreen()
            array[2] = entity.getBlue()

            array
        }

        MoveX -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getX()
            array
        }

        MoveY -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getY()
            array
        }

        MoveXY -> {
            val array = reusableArray.ensureSize(2)

            array[0] = entity.getX()
            array[1] = entity.getY()

            array
        }

        Width -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.width
            array
        }

        Height -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.height
            array
        }

        Size -> {
            val array = reusableArray.ensureSize(2)

            array[0] = entity.width
            array[1] = entity.height

            array
        }

        TranslateX -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.translationX
            array
        }

        TranslateY -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.translationY
            array
        }

        TranslateXY -> {
            val array = reusableArray.ensureSize(2)

            array[0] = entity.translationX
            array[1] = entity.translationY
            array
        }

        Rotation -> {
            val array = reusableArray.ensureSize(1)

            array[0] = entity.getRotation()
            array
        }

        else -> reusableArray
    }

    fun setValues(entity: ModifierTarget, initialValues: FloatArray, finalValues: FloatArray, percentage: Float) {
        fun valueAt(index: Int) = when {
            percentage >= 1f -> finalValues[index]
            percentage <= 0f -> initialValues[index]
            else -> initialValues[index] + percentage * (finalValues[index] - initialValues[index])
        }

        when (this) {

            ScaleX -> entity.setScaleX(valueAt(0))
            ScaleY -> entity.setScaleY(valueAt(0))
            ScaleXY -> entity.setScale(valueAt(0), valueAt(1))

            Alpha -> entity.setAlpha(valueAt(0))
            Color -> entity.setColor(valueAt(0), valueAt(1), valueAt(2))

            MoveX -> entity.setPosition(valueAt(0), entity.getY())
            MoveY -> entity.setPosition(entity.getX(), valueAt(0))
            MoveXY -> entity.setPosition(valueAt(0), valueAt(1))

            TranslateX -> entity.translationX = valueAt(0)
            TranslateY -> entity.translationY = valueAt(0)

            TranslateXY -> {
                entity.translationX = valueAt(0)
                entity.translationY = valueAt(1)
            }

            Width -> entity.width = valueAt(0)
            Height -> entity.height = valueAt(0)
            Size -> entity.setSize(valueAt(0), valueAt(1))

            Rotation -> entity.setRotation(valueAt(0))

            None -> Unit
        }
    }

    private fun FloatArray.ensureSize(size: Int) = this.takeIf { it.size >= size } ?: FloatArray(size)
}