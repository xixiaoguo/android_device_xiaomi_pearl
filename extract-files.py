#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixup_remove,
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

_displayservice_ping = (
    b'_ZN7lineage10frameworks14displayservice4V1_014IEventCallback4pingEv'
)
_displayservice_unresolved = (
    rb'_ZN7lineage10frameworks14displayservice4V1_01[45]I[A-Za-z0-9_]*'
    rb'(?:linkToDeath|unlinkToDeath|getDebugInfo|getHashChain|'
    rb'interfaceChain|interfaceDescriptor|5debug|registerForNotifications)'
    rb'[A-Za-z0-9_]*'
)
_displayservice_ctor_vtable = (
    rb'_ZTCN[A-Za-z0-9_]+E\d+_'
    rb'N7lineage10frameworks14displayservice4V1_01[45]I[A-Za-z0-9_]*E'
)

namespace_imports = [
    'device/xiaomi/pearl',
    'device/xiaomi/mt6895-common',
    'vendor/xiaomi/mt6895-common',
    'hardware/mediatek',
    'hardware/mediatek/libmtkperf_client',
    'hardware/xiaomi',
    'vendor/xiaomi/pearl'
]

lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
}

blob_fixups: blob_fixups_user_type = {
   	'vendor/bin/hw/android.hardware.security.keymint@1.0-service.mitee': blob_fixup()
		.replace_needed('android.hardware.security.keymint-V1-ndk_platform.so','android.hardware.security.keymint-V3-ndk-v34.so')
		.replace_needed('android.hardware.security.sharedsecret-V1-ndk_platform.so', 'android.hardware.security.sharedsecret-V1-ndk.so')
		.replace_needed('android.hardware.security.secureclock-V1-ndk_platform.so', 'android.hardware.security.secureclock-V1-ndk.so')
		.add_needed('android.hardware.security.rkp-V3-ndk.so'),

	'vendor/lib64/libmt_mitee@1.3.so': blob_fixup()
    .replace_needed('android.hardware.security.keymint-V1-ndk_platform.so','android.hardware.security.keymint-V3-ndk-v34.so'),

	('vendor/bin/hw/android.hardware.gnss-service.mediatek',
	'vendor/lib64/hw/android.hardware.gnss-impl-mediatek.so'): blob_fixup()
    .replace_needed('android.hardware.gnss-V1-ndk_platform.so','android.hardware.gnss-V1-ndk.so'),

    ('vendor/lib64/mt6895/libcam.hal3a.so',
     'vendor/lib64/mt6895/libcam.hal3a.ctrl.so',
     'vendor/lib64/mt6895/libmtkcam_request_requlator.so'): blob_fixup()
        .add_needed('libprocessgroup_shim.so'),

    ('vendor/lib64/mt6895/lib3a.flash.so',
     'vendor/lib64/mt6895/lib3a.sensors.color.so',
     'vendor/lib64/mt6895/lib3a.sensors.flicker.so'): blob_fixup()
        .add_needed('liblog.so'),

    'vendor/lib64/libalhLDC.so': blob_fixup()
        .add_needed('libnativewindow.so')
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),

    'vendor/lib64/libalLDC.so': blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),

    ('vendor/lib64/libnvram.so',
     'vendor/lib64/libsysenv.so',
     'vendor/bin/ioprofiler'): blob_fixup()
        .add_needed('libbase_shim.so'),

     'vendor/lib64/mt6895/libneuralnetworks_sl_driver_mtk_prebuilt.so': blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_createFromHandle')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_getNativeHandle')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_unlock')
        .add_needed('libbase_shim.so'),

    'vendor/lib64/mt6895/libmnl.so': blob_fixup()
        .add_needed('libcutils.so'),
 
     'vendor/bin/hw/android.hardware.media.c2@1.2-mediatek-64b': blob_fixup()
        .add_needed('libstagefright_foundation-v33.so')
        .add_needed('libprocessgroup.so')
        .add_needed('libprocessgroup_shim.so')
        .replace_needed('libavservices_minijail_vendor.so', 'libavservices_minijail.so')
        .replace_needed('libcodec2_hidl@1.0.so', 'libcodec2_hidl@1.0-v31.so')
        .replace_needed('libcodec2_hidl@1.1.so', 'libcodec2_hidl@1.1-v31.so')
        .replace_needed('libcodec2_hidl@1.2.so', 'libcodec2_hidl@1.2-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so'),

    'vendor/etc/vintf/manifest/manifest_media_c2_V1_2_default.xml': blob_fixup()
        .regex_replace('1.1', '1.2'),

    'vendor/lib64/hw/sensors.mediatek.V2.0.so': blob_fixup()
       .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),

    'vendor/lib64/libcodec2_hidl@1.0-v31.so': blob_fixup()
        .replace_needed('libstagefright_bufferqueue_helper.so', 'libstagefright_bufferqueue_helper-v35.so')
        .replace_needed('libcodec2_hidl_plugin.so', 'libcodec2_hidl_plugin-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so')
        .replace_needed('libui.so', 'libui-v32.so')
        .add_needed('libbase_shim.so'),

    'vendor/lib64/libcodec2_hidl@1.1-v31.so': blob_fixup()
        .replace_needed('libstagefright_bufferqueue_helper.so', 'libstagefright_bufferqueue_helper-v35.so')
        .replace_needed('libcodec2_hidl@1.0.so', 'libcodec2_hidl@1.0-v31.so')
        .replace_needed('libcodec2_hidl_plugin.so', 'libcodec2_hidl_plugin-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so')
        .replace_needed('libui.so', 'libui-v32.so')
        .add_needed('libbase_shim.so'),

    'vendor/lib64/libcodec2_hidl@1.2-v31.so': blob_fixup()
        .replace_needed('libstagefright_bufferqueue_helper.so', 'libstagefright_bufferqueue_helper-v35.so')
        .replace_needed('libcodec2_hidl@1.0.so', 'libcodec2_hidl@1.0-v31.so')
        .replace_needed('libcodec2_hidl@1.1.so', 'libcodec2_hidl@1.1-v31.so')
        .replace_needed('libcodec2_hidl_plugin.so', 'libcodec2_hidl_plugin-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so')
        .replace_needed('libui.so', 'libui-v32.so')
        .add_needed('libbase_shim.so'),

    'vendor/lib64/libcodec2_hidl_plugin-v31.so': blob_fixup()
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so'),

    ('vendor/lib64/libcodec2_mtk_c2store.so', 'vendor/lib64/libcodec2_vpp_dump_mtk_yuv_plugin.so', 'vendor/lib64/libcodec2_vpp_gc_plugin.so', 'vendor/lib64/libcodec2_vpp_qt_plugin.so', 'vendor/lib64/libcodec2_vpp_rs_plugin.so'): blob_fixup()
        .replace_needed('libcodec2_soft_common.so', 'libcodec2_soft_common-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so')
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so')
        .replace_needed('libsfplugin_ccodec_utils.so', 'libsfplugin_ccodec_utils-v31.so'),

    'vendor/lib64/libdolbyplugin.so': blob_fixup()
        .replace_needed('libcodec2_hidl@1.0.so', 'libcodec2_hidl@1.0-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so')
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so')
        .replace_needed('libui.so', 'libui-v32.so'),

    ('vendor/lib64/libcodec2_mtk_vdec.so', 'vendor/lib64/libcodec2_mtk_venc.so', 'vendor/lib64/libcodec2_vpp_dolby_plugin.so'): blob_fixup()
        .replace_needed('libcodec2_soft_common.so', 'libcodec2_soft_common-v31.so')
        .replace_needed('libcodec2_vndk.so', 'libcodec2_vndk-v31.so'),

    ('vendor/lib64/libcodec2_mtk_vdec.so', 'vendor/lib64/libcodec2_mtk_venc.so'): blob_fixup()
        .replace_needed('libformatter.so', 'libformatter_mtk.so'),

    ('vendor/lib/libcodec2_mtk_vdec.so', 'vendor/lib/libcodec2_mtk_venc.so'): blob_fixup()
        .replace_needed('libformatter.so', 'libformatter_mtk.so'),

    'vendor/lib64/mt6895/libmtkcam_hal_android_app_cbadaptor.so': blob_fixup()
        .replace_needed('android.frameworks.displayservice@1.0.so',
                        'lineage.frameworks.displayservice@1.0.so')
        .binary_regex_replace(
            b'7android10frameworks14displayservice',
            b'7lineage10frameworks14displayservice')
        .binary_regex_replace(
            _displayservice_unresolved,
            lambda m: _displayservice_ping.ljust(len(m.group(0)), b'\x00'))
        .binary_regex_replace(
            _displayservice_ctor_vtable,
            lambda m: _displayservice_ping.ljust(len(m.group(0)), b'\x00')),

    ('vendor/lib64/mt6895/libmtkcam_stdutils.so',
     'vendor/lib64/hw/mt6895/android.hardware.camera.provider@2.6-impl-mediatek.so'): blob_fixup()
        .replace_needed('libutils.so', 'libutils-v32.so'),

    'vendor/bin/hw/mtkfusionrild': blob_fixup()
        .add_needed('libutils-v32.so'),

    'vendor/lib64/hw/mt6895/vendor.mediatek.hardware.pq@2.15-impl.so': blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v34.so')
        .replace_needed('libutils.so', 'libutils-v32.so')
        .replace_needed('libsensorndkbridge.so', 'android.hardware.sensors@1.0-convert-shared.so'),

    ('vendor/lib64/mt6895/libaalservice.so',
     'vendor/bin/mnld'): blob_fixup()
        .replace_needed('libsensorndkbridge.so', 'android.hardware.sensors@1.0-convert-shared.so'),

    'vendor/etc/sensors/hals.conf': blob_fixup()
        .regex_replace('android.hardware.sensors@2.X-subhal-mediatek.so', 'android.hardware.sensors@2.0-subhal-impl-1.0.so')
        .regex_replace('sensors.touch.detect.so', 'sensors.dynamic_sensor_hal.so'),

    'vendor/lib64/hw/audio.primary.mediatek.so': blob_fixup()
        .add_needed('libstagefright_foundation-v33.so')
        .replace_needed('libalsautils.so', 'libalsautils-v31.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v34.so'),

    'vendor/etc/libnfc-nci.conf': blob_fixup()
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0'),

    ('vendor/etc/libnfc-nxp.conf',
     'vendor/etc/libnfc-nxp-pnscr.conf'): blob_fixup()
        .regex_replace('(NXPLOG_.*_LOGLEVEL)=0x03', '\\1=0x02')
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0'),

    'vendor/bin/mi_thermald': blob_fixup()
        .binary_regex_replace(b'%d/on', b'%d/..'),

    'vendor/etc/init/android.hardware.neuralnetworks-shim-service-mtk.rc': blob_fixup()
        .regex_replace('start', 'enable'),

    'vendor/etc/public.libraries.txt': blob_fixup()
        .add_line_if_missing('libmpbase.so'),

    ('vendor/lib/libnvram.so',
     'vendor/lib/libsysenv.so'): blob_fixup()
        .add_needed('libbase_shim.so'),

    'vendor/lib/mt6895/libneuralnetworks_sl_driver_mtk_prebuilt.so': blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_createFromHandle')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_getNativeHandle')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_unlock')
        .add_needed('libbase_shim.so'),

}  # fmt: skip

module = ExtractUtilsModule(
    'pearl',
    'xiaomi',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
    add_firmware_proprietary_file=False,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
