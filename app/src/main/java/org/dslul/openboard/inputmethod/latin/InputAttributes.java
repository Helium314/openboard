/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.os.Build;
import android.text.InputType;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import android.view.inputmethod.EditorInfo;

import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils;

import java.util.ArrayList;
import java.util.Arrays;

import static org.dslul.openboard.inputmethod.latin.common.Constants.ImeOption.NO_FLOATING_GESTURE_PREVIEW;
import static org.dslul.openboard.inputmethod.latin.common.Constants.ImeOption.NO_MICROPHONE;

/**
 * Class to hold attributes of the input field.
 */
public final class InputAttributes {
    private final String TAG = InputAttributes.class.getSimpleName();

    final public String mTargetApplicationPackageName;
    final public boolean mInputTypeShouldAutoCorrect;
    final public boolean mIsPasswordField;
    final public boolean mShouldShowSuggestions;
    final public boolean mMayOverrideShowingSuggestions;
    final public boolean mApplicationSpecifiedCompletionOn;
    final public boolean mShouldInsertSpacesAutomatically;
    final public boolean mShouldShowVoiceInputKey;
    final public boolean mNoLearning;
    /**
     * Whether the floating gesture preview should be disabled. If true, this should override the
     * corresponding keyboard settings preference, always suppressing the floating preview text.
     * {@link org.dslul.openboard.inputmethod.latin.settings.SettingsValues#mGestureFloatingPreviewTextEnabled}
     */
    final public boolean mDisableGestureFloatingPreviewText;
    final public boolean mIsGeneralTextInput;
    final public int mInputType;
    final private EditorInfo mEditorInfo;
    final private String mPackageNameForPrivateImeOptions;

    public InputAttributes(final EditorInfo editorInfo, final boolean isFullscreenMode,
            final String packageNameForPrivateImeOptions) {
        mEditorInfo = editorInfo;
        mPackageNameForPrivateImeOptions = packageNameForPrivateImeOptions;
        mTargetApplicationPackageName = null != editorInfo ? editorInfo.packageName : null;
        final int inputType = null != editorInfo ? editorInfo.inputType : 0;
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        mInputType = inputType;
        mIsPasswordField = InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType);
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            // If we are not looking at a TYPE_CLASS_TEXT field, the following strange
            // cases may arise, so we do a couple sanity checks for them. If it's a
            // TYPE_CLASS_TEXT field, these special cases cannot happen, by construction
            // of the flags.
            if (null == editorInfo) {
                Log.w(TAG, "No editor info for this field. Bug?");
            } else if (InputType.TYPE_NULL == inputType) {
                // TODO: We should honor TYPE_NULL specification.
                Log.i(TAG, "InputType.TYPE_NULL is specified");
            } else if (inputClass == 0) {
                // TODO: is this check still necessary?
                Log.w(TAG, String.format("Unexpected input class: inputType=0x%08x"
                        + " imeOptions=0x%08x", inputType, editorInfo.imeOptions));
            }
            mShouldShowSuggestions = false;
            mMayOverrideShowingSuggestions = false;
            mInputTypeShouldAutoCorrect = false;
            mApplicationSpecifiedCompletionOn = false;
            mShouldInsertSpacesAutomatically = false;
            mShouldShowVoiceInputKey = false;
            mDisableGestureFloatingPreviewText = false;
            mIsGeneralTextInput = false;
            mNoLearning = false;
            return;
        }

        // inputClass == InputType.TYPE_CLASS_TEXT
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        final boolean flagNoSuggestions = 0 != (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final boolean flagMultiLine = 0 != (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final boolean flagAutoCorrect = 0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        final boolean flagAutoComplete = 0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);

        // TODO: Have a helper method in InputTypeUtils
        // Make sure that passwords are not displayed in {@link SuggestionStripView}.
        mShouldShowSuggestions = !mIsPasswordField && !flagNoSuggestions;
        mMayOverrideShowingSuggestions = !mIsPasswordField;

        mShouldInsertSpacesAutomatically = InputTypeUtils.isAutoSpaceFriendlyType(inputType);

        final boolean noMicrophone = mIsPasswordField
                || InputTypeUtils.isEmailVariation(variation)
                || InputType.TYPE_TEXT_VARIATION_URI == variation
                || hasNoMicrophoneKeyOption()
                || !RichInputMethodManager.getInstance().hasShortcutIme();
        mShouldShowVoiceInputKey = !noMicrophone;

        mDisableGestureFloatingPreviewText = InputAttributes.inPrivateImeOptions(
                mPackageNameForPrivateImeOptions, NO_FLOATING_GESTURE_PREVIEW, editorInfo);

        // autocorrect if explicitly wanted, but also for most multi-line input types (like AOSP keyboard)
        // originally, URI and email were always excluded from autocorrect (in Suggest.java), but this is
        //  and unexpected place, and if the input field explicitly requests autocorrect we should follow the flag
        mInputTypeShouldAutoCorrect = flagAutoCorrect || (
                flagMultiLine
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                && variation != InputType.TYPE_TEXT_VARIATION_URI
                && !InputTypeUtils.isEmailVariation(variation)
                && !flagNoSuggestions
        );

        mApplicationSpecifiedCompletionOn = flagAutoComplete && isFullscreenMode;

        // If we come here, inputClass is always TYPE_CLASS_TEXT
        mIsGeneralTextInput = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != variation
                && InputType.TYPE_TEXT_VARIATION_PASSWORD != variation
                && InputType.TYPE_TEXT_VARIATION_PHONETIC != variation
                && InputType.TYPE_TEXT_VARIATION_URI != variation
                && InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != variation
                && InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != variation
                && InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != variation;


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mNoLearning = (editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        else
            mNoLearning = false;
    }

    public boolean isTypeNull() {
        return InputType.TYPE_NULL == mInputType;
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return editorInfo.inputType == mInputType && mEditorInfo != null
                && (mEditorInfo.imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) == (editorInfo.imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII);
    }

    private boolean hasNoMicrophoneKeyOption() {
        return InputAttributes.inPrivateImeOptions(mPackageNameForPrivateImeOptions, NO_MICROPHONE, mEditorInfo);
    }

    @SuppressWarnings("unused")
    private void dumpFlags(final int inputType) {
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        final String inputClassString = toInputClassString(inputClass);
        final String variationString = toVariationString(
                inputClass, inputType & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final String flagsString = toFlagsString(inputType & InputType.TYPE_MASK_FLAGS);
        Log.i(TAG, "Input class: " + inputClassString);
        Log.i(TAG, "Variation: " + variationString);
        Log.i(TAG, "Flags: " + flagsString);
    }

    private static String toInputClassString(final int inputClass) {
        switch (inputClass) {
        case InputType.TYPE_CLASS_TEXT:
            return "TYPE_CLASS_TEXT";
        case InputType.TYPE_CLASS_PHONE:
            return "TYPE_CLASS_PHONE";
        case InputType.TYPE_CLASS_NUMBER:
            return "TYPE_CLASS_NUMBER";
        case InputType.TYPE_CLASS_DATETIME:
            return "TYPE_CLASS_DATETIME";
        default:
            return String.format("unknownInputClass<0x%08x>", inputClass);
        }
    }

    private static String toVariationString(final int inputClass, final int variation) {
        switch (inputClass) {
        case InputType.TYPE_CLASS_TEXT:
            return toTextVariationString(variation);
        case InputType.TYPE_CLASS_NUMBER:
            return toNumberVariationString(variation);
        case InputType.TYPE_CLASS_DATETIME:
            return toDatetimeVariationString(variation);
        default:
            return "";
        }
    }

    private static String toTextVariationString(final int variation) {
        switch (variation) {
        case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
            return " TYPE_TEXT_VARIATION_EMAIL_ADDRESS";
        case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
            return "TYPE_TEXT_VARIATION_EMAIL_SUBJECT";
        case InputType.TYPE_TEXT_VARIATION_FILTER:
            return "TYPE_TEXT_VARIATION_FILTER";
        case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
            return "TYPE_TEXT_VARIATION_LONG_MESSAGE";
        case InputType.TYPE_TEXT_VARIATION_NORMAL:
            return "TYPE_TEXT_VARIATION_NORMAL";
        case InputType.TYPE_TEXT_VARIATION_PASSWORD:
            return "TYPE_TEXT_VARIATION_PASSWORD";
        case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
            return "TYPE_TEXT_VARIATION_PERSON_NAME";
        case InputType.TYPE_TEXT_VARIATION_PHONETIC:
            return "TYPE_TEXT_VARIATION_PHONETIC";
        case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
            return "TYPE_TEXT_VARIATION_POSTAL_ADDRESS";
        case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
            return "TYPE_TEXT_VARIATION_SHORT_MESSAGE";
        case InputType.TYPE_TEXT_VARIATION_URI:
            return "TYPE_TEXT_VARIATION_URI";
        case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
            return "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD";
        case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
            return "TYPE_TEXT_VARIATION_WEB_EDIT_TEXT";
        case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
            return "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS";
        case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            return "TYPE_TEXT_VARIATION_WEB_PASSWORD";
        default:
            return String.format("unknownVariation<0x%08x>", variation);
        }
    }

    private static String toNumberVariationString(final int variation) {
        switch (variation) {
        case InputType.TYPE_NUMBER_VARIATION_NORMAL:
            return "TYPE_NUMBER_VARIATION_NORMAL";
        case InputType.TYPE_NUMBER_VARIATION_PASSWORD:
            return "TYPE_NUMBER_VARIATION_PASSWORD";
        default:
            return String.format("unknownVariation<0x%08x>", variation);
        }
    }

    private static String toDatetimeVariationString(final int variation) {
        switch (variation) {
        case InputType.TYPE_DATETIME_VARIATION_NORMAL:
            return "TYPE_DATETIME_VARIATION_NORMAL";
        case InputType.TYPE_DATETIME_VARIATION_DATE:
            return "TYPE_DATETIME_VARIATION_DATE";
        case InputType.TYPE_DATETIME_VARIATION_TIME:
            return "TYPE_DATETIME_VARIATION_TIME";
        default:
            return String.format("unknownVariation<0x%08x>", variation);
        }
    }

    private static String toFlagsString(final int flags) {
        final ArrayList<String> flagsArray = new ArrayList<>();
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
            flagsArray.add("TYPE_TEXT_FLAG_NO_SUGGESTIONS");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_MULTI_LINE))
            flagsArray.add("TYPE_TEXT_FLAG_MULTI_LINE");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE))
            flagsArray.add("TYPE_TEXT_FLAG_IME_MULTI_LINE");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_CAP_WORDS))
            flagsArray.add("TYPE_TEXT_FLAG_CAP_WORDS");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
            flagsArray.add("TYPE_TEXT_FLAG_CAP_SENTENCES");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
            flagsArray.add("TYPE_TEXT_FLAG_CAP_CHARACTERS");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
            flagsArray.add("TYPE_TEXT_FLAG_AUTO_CORRECT");
        if (0 != (flags & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))
            flagsArray.add("TYPE_TEXT_FLAG_AUTO_COMPLETE");
        return flagsArray.isEmpty() ? "" : Arrays.toString(flagsArray.toArray());
    }

    // Pretty print
    @Override
    public String toString() {
        return String.format(
                "%s: inputType=0x%08x%s%s%s%s%s targetApp=%s\n", getClass().getSimpleName(),
                mInputType,
                (mInputTypeShouldAutoCorrect ? " noAutoCorrect" : ""),
                (mIsPasswordField ? " password" : ""),
                (mShouldShowSuggestions ? " shouldShowSuggestions" : ""),
                (mApplicationSpecifiedCompletionOn ? " appSpecified" : ""),
                (mShouldInsertSpacesAutomatically ? " insertSpaces" : ""),
                mTargetApplicationPackageName);
    }

    public static boolean inPrivateImeOptions(final String packageName, final String key,
            final EditorInfo editorInfo) {
        if (editorInfo == null) return false;
        final String findingKey = (packageName != null) ? packageName + "." + key : key;
        return StringUtils.containsInCommaSplittableText(findingKey, editorInfo.privateImeOptions);
    }
}
