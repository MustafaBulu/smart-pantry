package com.mustafabulu.smartpantry.core.response;

public final class ResponseMessages {

    public static final String INVALID_MARKETPLACE_CODE = "Invalid marketplace code.";
    public static final String CATEGORY_NOT_FOUND = "Category not found.";
    public static final String PRODUCT_COULD_NOT_BE_ADDED = "Product could not be added.";
    public static final String PRODUCT_ADDED = "Product added.";
    public static final String PRODUCT_NOT_FOUND = "Product not found.";
    public static final String PRODUCT_NOT_FOUND_CODE = "PRODUCT_NOT_FOUND";
    public static final String ALREADY_ADDED = "Already added";
    public static final String ALREADY_ADDED_CODE = "ALREADY_ADDED";
    public static final String UNKNOWN_EXCEPTION = "Unknown exception";
    public static final String NOT_VALID = "NOT_VALID";
    public static final String UNKNOWN_ERR = "UNKNOWN_ERR";
    public static final String MIGROS_FETCH_FAILED = "Failed to fetch Migros product details from %s";
    public static final String YEMEKSEPETI_FETCH_FAILED = "Failed to fetch product details from %s";
    public static final String YEMEKSEPETI_FETCH_FAILED_CODE = "YEMEKSEPETI_FETCH_FAILED";
    public static final String CATEGORY_NAME_REQUIRED = "Category name is required.";
    public static final String CATEGORY_NAME_REQUIRED_CODE = "CATEGORY_NAME_REQUIRED";
    public static final String CATEGORY_ALREADY_EXISTS = "Category already exists.";
    public static final String CATEGORY_ALREADY_EXISTS_CODE = "CATEGORY_ALREADY_EXISTS";
    public static final String CATEGORY_IN_USE = "Category is in use.";
    public static final String CATEGORY_IN_USE_CODE = "CATEGORY_IN_USE";
    public static final String CATEGORY_NOT_FOUND_CODE = "CATEGORY_NOT_FOUND";
    public static final String PRODUCT_UPDATE_EMPTY = "No fields provided to update.";
    public static final String PRODUCT_UPDATE_EMPTY_CODE = "PRODUCT_UPDATE_EMPTY";
    public static final String MARKETPLACE_PRODUCT_NOT_FOUND = "Marketplace product not found.";
    public static final String MARKETPLACE_PRODUCT_AMBIGUOUS = "Multiple marketplace products found. Provide categoryName.";
    public static final String MARKETPLACE_REFRESH_FAILED = "Product refresh failed.";
    public static final String MARKETPLACE_REFRESHED = "Product refreshed.";
    public static final String MARKETPLACE_PRODUCT_REMOVED = "Marketplace product removed.";
    public static final String PRODUCT_REMOVED = "Product removed.";
    public static final String CATEGORY_REMOVED = "Category removed.";

    private ResponseMessages() {
    }
}
