#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from mt6895-common
$(call inherit-product, device/xiaomi/mt6895-common/mt6895.mk)

# Display
$(call soong_config_set_bool,surfaceflinger,register_displayservice,true)

# MIUI Camera
$(call inherit-product, vendor/xiaomi/miuicamera-pearl/device.mk)

# Audio (device-specific effect config)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml

# Dolby (DAP engine, ported from yuechu OS3.0.10.0). Effect libs come from
# the proprietary blob list, registered in audio_effects.xml.
#PRODUCT_PACKAGES += \
#    XiaomiDolby

# libdlbdsservice.so (in the dms daemon) pulls in libsqlite -> libandroidicu ->
# libicuuc/libicui18n, which only ship inside the com.android.i18n APEX that
# the A16 vendor linker namespace cannot see. Vendor these three as prebuilt
# shared libraries (prebuilts/dolby_icu/Android.bp) reusing external/icu
# module names via stem.
PRODUCT_PACKAGES += \
    libandroidicu-dolby-vendor \
    libicuuc-dolby-vendor \
    libicui18n-dolby-vendor

# ConsumerIR
PRODUCT_PACKAGES += \
    android.hardware.ir-service.example

# Dynamic Partitions (device fastboot package)
PRODUCT_FASTBOOT_TEMPLATE_ZIP := $(LOCAL_PATH)/prebuilts/fastboot.zip

# FM Radio
PRODUCT_PACKAGES += \
    FMRadio \
    FmRecordingsProvider

# Media (device-specific codec config)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/media/media_codecs_dolby_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_dolby_audio.xml

# NFC
PRODUCT_PACKAGES += \
    android.hardware.nfc-service.nxp \
    com.android.nfc_extras \
    Tag

PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.nfc.ese.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.nfc.ese.xml \
        frameworks/native/data/etc/android.hardware.nfc.hce.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.nfc.hce.xml \
        frameworks/native/data/etc/android.hardware.nfc.hcef.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.nfc.hcef.xml \
        frameworks/native/data/etc/android.hardware.nfc.uicc.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.nfc.uicc.xml \
        frameworks/native/data/etc/android.hardware.nfc.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.nfc.xml \
        frameworks/native/data/etc/android.hardware.se.omapi.ese.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.se.omapi.ese.xml \
        frameworks/native/data/etc/android.hardware.se.omapi.uicc.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/android.hardware.se.omapi.uicc.xml \
        frameworks/native/data/etc/com.android.nfc_extras.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/com.android.nfc_extras.xml \
        frameworks/native/data/etc/com.nxp.mifare.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/com.nxp.mifare.xml

# Overlays
PRODUCT_PACKAGES += \
    FrameworksResOverlayPearl \
    NfcOverlayPearl \
    SettingsProviderOverlayPearl \
    SystemUIResOverlayPearl \
    WifiResOverlayPearl

# Parts
PRODUCT_PACKAGES += \
    XiaomiParts

# Remove unwanted packages
PRODUCT_PACKAGES += \
    RemovePkgs

# Radio
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(LOCAL_PATH)/configs/rsc,$(TARGET_COPY_OUT_VENDOR)/etc/rsc)

# Rootdir
PRODUCT_PACKAGES += \
    init.project.rc \
    init.pearl.rc

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Touchscreen firmware, also needed in recovery where /vendor is not mounted
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_nt36672e_l16s_fw01.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_nt36672e_l16s_fw01.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_nt36672e_l16s_fw02.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_nt36672e_l16s_fw02.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_nt36672e_l16s_mp01.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_nt36672e_l16s_mp01.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_nt36672e_l16s_mp02.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_nt36672e_l16s_mp02.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_ts_fw01.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_ts_fw01.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_ts_fw02.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_ts_fw02.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_ts_mp01.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_ts_mp01.bin \
    $(LOCAL_PATH)/../../../vendor/xiaomi/pearl/proprietary/vendor/firmware/novatek_ts_mp02.bin:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/vendor/firmware/novatek_ts_mp02.bin

# Inherit the proprietary files
$(call inherit-product, vendor/xiaomi/pearl/pearl-vendor.mk)
PRODUCT_DEFAULT_DEV_CERTIFICATE := .android-certs/releasekey

# Use the device keys for the Bluetooth sepolicy context so the Bluetooth
# certificate in mac_permissions.xml matches the one used to sign the
# Bluetooth APK. Otherwise com.android.bluetooth gets the default seinfo
# and zygote fails to set its SELinux context, breaking Bluetooth.
PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES := .android-certs/


# use pearl-specific OS3 c2store (disable the shared/default one for pearl only)

# pearl-only: use OS3 c2store via the shared hook (default for others = common src)
$(call soong_config_set, xiaomi_pearl, c2store_src64, pearl)
