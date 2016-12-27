package com.yennkasa.util;

import java.util.HashSet;
import java.util.Set;

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

}
