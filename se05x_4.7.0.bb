DESCRIPTION = "NXP Plug and Trust Middleware"
LICENSE = "CLOSED & MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# ATTENTION:
# - The NXP-PLUG-TRUST-MW variable defined below
# - The verbatim instantiation of the package name in the function pkg_postinst_ontarget_${PN}
#   defined at the end of this file
# must match the root name (i.e. excluding the .zip extension) of the downloaded Plug&Trust zip file
#
# Please replace the value 'SE-PLUG-TRUST-MW_04.07.00' - used twice in this script - by the
# exact root name of the package if required.
#
# [Update value]
# **************
NXP-PLUG-TRUST-MW = "SE-PLUG-TRUST-MW_04.07.00"
#

inherit cmake dos2unix
DEPENDS = "openssl python3 systemd python3-virtualenv"
RDEPENDS:${PN} += "bash"
RDEPENDS:${PN} += "zsh"
RDEPENDS:${PN} += "python3-misc"
RDEPENDS:${PN} += "python3-virtualenv"

# Allow symlink .so in non-dev package
INSANE_SKIP:${PN} += " dev-so"

# Avoid warning of local lib path
INSANE_SKIP:${PN} += "libdir"

S = "${WORKDIR}/simw-top"
B = "${WORKDIR}/build"

SRC_URI = "file://${NXP-PLUG-TRUST-MW}.zip    \
           file://px-ssscli.sh \
           git://bitbucket.org/pixeltechnologies/mb8-a53-tools.git;protocol=ssh;branch=main;name=extratgz;user=git \
           "

SRCREV_extratgz = "${AUTOREV}"
EXTRATGZ_DIR = "${WORKDIR}/git"

do_configure() {
    cd ${B}

    cmake -S ../simw-top \
        -DOPENSSL_INSTALL_PREFIX=${WORKDIR}/recipe-sysroot/usr/ \
        -DPAHO_BUILD_DEB_PACKAGE=OFF \
        -DPAHO_BUILD_DOCUMENTATION=OFF \
        -DPAHO_BUILD_SAMPLES=OFF \
        -DPAHO_BUILD_SHARED=ON \
        -DPAHO_BUILD_STATIC=OFF \
        -DPAHO_ENABLE_CPACK=ON \
        -DPAHO_ENABLE_TESTING=OFF \
        -DPAHO_WITH_SSL=ON \
        -DPTMW_FIPS=None \
        -DPTMW_CMAKE_BUILD_TYPE=Release \
        -DPTMW_Host=iMXLinux \
        -DPTMW_HostCrypto=OPENSSL \
        -DPTMW_Log=Default \
        -DPTMW_OpenSSL=3_0 \
        -DPTMW_RTOS=Default \
        -DPTMW_SBL=None \
        -DPTMW_SCP=SCP03_SSS \
        -DPTMW_SMCOM=T1oI2C \
        -DPTMW_SE05X_Auth=None \
        -DPTMW_mbedTLS_ALT=None \
        -DSSSFTR_SE05X_AES=ON \
        -DSSSFTR_SE05X_AuthECKey=ON \
        -DSSSFTR_SE05X_AuthSession=ON \
        -DSSSFTR_SE05X_CREATE_DELETE_CRYPTOOBJ=ON \
        -DSSSFTR_SE05X_ECC=ON \
        -DSSSFTR_SE05X_KEY_GET=ON \
        -DSSSFTR_SE05X_KEY_SET=ON \
        -DSSSFTR_SE05X_RSA=ON \
        -DSSSFTR_SW_AES=ON \
        -DSSSFTR_SW_ECC=ON \
        -DSSSFTR_SW_KEY_GET=ON \
        -DSSSFTR_SW_KEY_SET=ON \
        -DSSSFTR_SW_RSA=ON \
        -DSSSFTR_SW_TESTCOUNTERPART=ON \
        -DWithAccessMgr_UnixSocket=OFF \
        -DWithCodeCoverage=OFF \
        -DWithExtCustomerTPMCode=OFF \
        -DWithNXPNFCRdLib=OFF \
        -DWithOPCUA_open62541=OFF \
        -DPTMW_Applet=SE05X_C \
        -DPTMW_SE05X_Ver=03_XX  \
        -DWithSharedLIB=ON \
        -DOPENSSL_ROOT_DIR=${WORKDIR}/recipe-sysroot/usr/
}

do_compile() {
    oe_runmake
}

do_install:append() {
    # Install Unzipped Plug&Trust MW package in /root
    # Resulting unzipped Package deviates from the one published on www.nxp.com in the following respect:
    # - Source code patches (if any) have been applied (refer to se05x bitbake recipe)
    # - ext/mcu-sdk directory removed
    # - sss/plugin/openssl/bin/libsss_engine.so removed
    # - tools/libsssapisw.so removed
    echo "S=${S}"
    echo "D=${D}"
    install -d ${S} ${D}/root/${NXP-PLUG-TRUST-MW}
    cp -fr --no-preserve=ownership ${S} ${D}/root/${NXP-PLUG-TRUST-MW}
    rm -rf ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/binaries/ex
    rm -rf ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/binaries/PCLinux64
    rm -rf ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/ext/mcu-sdk
    rm -rf ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/ext/openssl/lib_mingw/
    rm -f ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/sss/plugin/openssl/bin/libsss_engine.so
    rm -f ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/sss/plugin/openssl_provider/bin/libsssProvider.so
    rm -f ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/tools/libsssapisw.so
    find ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top -name "*.sh" -exec chmod 755 {} \;

    mkdir -p ${D}/root/install
    cp --no-preserve=ownership ${D}/root/${NXP-PLUG-TRUST-MW}/simw-top/scripts/imx/se05x_mw_install.sh ${D}/root/install
    echo "Use se05x_mw_install.sh to install an update of the package" > ${D}/root/install/readme.txt

    install -d ${D}/root
    tar -xzf ${EXTRATGZ_DIR}/ssscli_env.tgz -C ${D}/root

    mkdir -p ${D}/etc/ld.so.conf.d
    echo /usr/local/lib > ${D}/etc/ld.so.conf.d/se05x.conf

    install -d ${D}/etc/profile.d/
    install -m 0644 ${WORKDIR}/px-ssscli.sh ${D}/etc/profile.d/px-ssscli.sh

    install -d ${D}/usr/local/lib
    install -m 0644 ${B}/nxp_iot_agent/ex/src/libcore_json.so ${D}/usr/local/lib/

    install -d ${D}/pixel/auth
    install -m 0644 ${S}/demos/linux/common/openssl30_sss_se050.cnf ${D}/pixel/auth
}

FILES:${PN} = " \
    /usr/local/lib/*.so \
    /usr/local/bin/* \
    /pixel/auth/* \
    /root/${NXP-PLUG-TRUST-MW} \
    /root/ssscli_env \
    /root/install \
    /usr/local/lib/libpaho-*.so /usr/local/lib/libpaho-*.so.* \
    /etc/ld.so.conf.d/se05x.conf \
    /etc/profile.d/px-ssscli.sh \
    /usr/bin/ssscli \
    "

FILES:${PN}-dev = "        \
    /usr/local/include/*   \
    /usr/local/share/*     \
    /usr/local/lib/cmake/* \
    "

FILES:${PN}-staticdev = "/usr/local/lib/*.a*"
