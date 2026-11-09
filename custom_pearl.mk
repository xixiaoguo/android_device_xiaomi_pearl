#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit some common PixelOS stuff.
$(call inherit-product, vendor/custom/config/common_full_phone.mk)

# Inherit from pearl device
$(call inherit-product, device/xiaomi/pearl/device.mk)

# Keep the unrelated Yuechu device tree and its vendor tree out of this product's Soong graph.
PRODUCT_SOURCE_ROOT_DIRS += -device/xiaomi/yuechu -vendor/xiaomi/yuechu


PRODUCT_DEVICE := pearl
PRODUCT_NAME := custom_pearl
PRODUCT_BRAND := Redmi
PRODUCT_MODEL := 23054RA19C
PRODUCT_MANUFACTURER := Xiaomi

PRODUCT_SYSTEM_NAME := pearl
PRODUCT_SYSTEM_DEVICE := pearl
PRODUCT_CHARACTERISTICS := nosdcard

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

TARGET_SCREEN_WIDTH := 1080

# Enable DM file pre-opting to reduce first boot time
# Note that this may significantly increase your compilation time!
PRODUCT_DEX_PREOPT_GENERATE_DM_FILES := true

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=Redmi/pearl/pearl:15/AP3A.240905.015.A2/OS3.0.3.0.VLHCNXM:user/release-keys \
    BuildDesc="pearl-user 15 AP3A.240905.015.A2 OS3.0.3.0.VLHCNXM release-keys" \
    DeviceName=$(PRODUCT_SYSTEM_DEVICE) \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

# Set BUILD_FINGERPRINT variable to be picked up by both system and vendor build.prop
BUILD_FINGERPRINT := Redmi/pearl/pearl:15/AP3A.240905.015.A2/OS3.0.3.0.VLHCNXM:user/release-keys


# 相机：改用 MIUI Camera（见 device.mk 里的 inherit-product）；设备树内已无 Aperture 源码。
PRODUCT_PACKAGES := $(filter-out Aperture,$(PRODUCT_PACKAGES))
