package com.pair.util;

import android.widget.EditText;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
@SuppressWarnings("unused")
public class FormValidator {
    Set<ValidationStrategy> strategies;


    public FormValidator() {
        this.strategies = new HashSet<>();
    }

    public FormValidator addStrategy(ValidationStrategy strategy) {
        this.strategies.add(strategy);
        return this;
    }

    public void dismissStrategy(ValidationStrategy strategy) {
        //noinspection SuspiciousMethodCalls
        this.strategies.remove(strategy);
    }

    public boolean runValidation() {
        for (ValidationStrategy strategy : strategies) {
            if (!strategy.validate()) {
                return false;
            }
        }
        return true;
    }

    public interface ValidationStrategy {
        boolean validate();
    }

    public final class EditTextValidationStrategy implements ValidationStrategy {
        private final EditText textField;
        private final Pattern pattern;

        public EditTextValidationStrategy(EditText editText, Pattern regExp) {
            this.textField = editText;
            this.pattern = regExp;
        }
        @Override
        public boolean validate() {
            //TODO make use of the passed pattern
            String fieldContent = UiHelpers.getFieldContent(textField);
            Matcher matcher = pattern.matcher(fieldContent);
            return matcher.matches();
        }
    }

    //TEXT_FIELD_NOT_EMPTY
}
