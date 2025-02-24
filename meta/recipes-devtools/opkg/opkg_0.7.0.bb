SUMMARY = "Open Package Manager"
SUMMARY:libopkg = "Open Package Manager library"
SECTION = "base"
HOMEPAGE = "https://git.yoctoproject.org/opkg/"
DESCRIPTION = "Opkg is a lightweight package management system based on Ipkg."
BUGTRACKER = "https://bugzilla.yoctoproject.org/buglist.cgi?quicksearch=Product%3Aopkg"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=94d55d512a9ba36caa9b7df079bae19f \
                    file://src/opkg.c;beginline=4;endline=18;md5=d6200b0f2b41dee278aa5fad333eecae"

DEPENDS = "libarchive zstd"

PE = "1"

SRC_URI = "git://github.com/Ecordonnier/opkg.git;protocol=https;branch=eco/cmake \
           file://opkg.conf \
           file://0001-opkg_conf-create-opkg.lock-in-run-instead-of-var-run.patch \
           file://run-ptest \
           "

SRCREV="${AUTOREV}"
#"2d018cbcbed639fb1828cdcd681ed70af9113c67"
S = "${WORKDIR}/git"
#SRC_URI[sha256sum] = "d973fd0f1568f58f87d6aecd9aa95e3e1f60214a45cee26704bf8fe757c54567"

# This needs to be before ptest inherit, otherwise all ptest files end packaged
# in libopkg package if OPKGLIBDIR == libdir, because default
# PTEST_PATH ?= "${libdir}/${BPN}/ptest"
PACKAGES =+ "libopkg"

inherit cmake pkgconfig ptest

target_localstatedir := "${localstatedir}"
OPKGLIBDIR ??= "${target_localstatedir}/lib"

PACKAGECONFIG ??= "libsolv"

PACKAGECONFIG[gpg] = "-DHAVE_GPGME=ON,-DHAVE_GPGME=OFF,\
    gnupg gpgme libgpg-error,\
    ${@ "gnupg" if ("native" in d.getVar("PN")) else "gnupg-gpg"}\
    "
PACKAGECONFIG[curl] = "-DHAVE_CURL=0N,-DHAVE_CURL=OFF,curl"
PACKAGECONFIG[ssl-curl] = "-DHAVE_SSLCURL=ON,-DHAVE_SSLCURL=OFF,curl openssl"
PACKAGECONFIG[sha256] = "-DHAVE_SHA256=ON,-DHAVE_SHA256=OFF"
PACKAGECONFIG[libsolv] = "-DHAVE_SOLVER_LIBSOLV=ON,-DHAVE_SOLVER_LIBSOLV=OFF,libsolv"

EXTRA_OECMAKE = "-DHAVE_ZSTD=ON"
EXTRA_OECMAKE:append:class-native = " -DVARDIR=/${@os.path.relpath('${localstatedir}', '${STAGING_DIR_NATIVE}')} -DSYSCONFDIR=/${@os.path.relpath('${sysconfdir}', '${STAGING_DIR_NATIVE}')}"

do_install:append () {
	install -d ${D}${sysconfdir}/opkg
	install -m 0644 ${UNPACKDIR}/opkg.conf ${D}${sysconfdir}/opkg/opkg.conf
	echo "option lists_dir   ${OPKGLIBDIR}/opkg/lists"  >>${D}${sysconfdir}/opkg/opkg.conf
	echo "option info_dir    ${OPKGLIBDIR}/opkg/info"   >>${D}${sysconfdir}/opkg/opkg.conf
	echo "option status_file ${OPKGLIBDIR}/opkg/status" >>${D}${sysconfdir}/opkg/opkg.conf

	# We need to create the lock directory
	install -d ${D}${OPKGLIBDIR}/opkg
}

do_install_ptest () {
    # the ptest class expects a Makefile, but cmake uses Ninja per default so we need to install ptests manually:
    cp -r ${S}/tests ${D}${PTEST_PATH}

    sed -i -e '/@echo $^/d' ${D}${PTEST_PATH}/tests/Makefile
    sed -i -e '/@PYTHONPATH=. $(PYTHON) $^/a\\t@if [ "$$?" != "0" ];then echo "FAIL:"$^;else echo "PASS:"$^;fi' ${D}${PTEST_PATH}/tests/Makefile
}

WARN_QA:append = " internal-solver-deprecation"
QARECIPETEST[internal-solver-deprecation] = "qa_check_solver_deprecation"
def qa_check_solver_deprecation (pn, d):
    pkgconfig = (d.getVar("PACKAGECONFIG") or "").split()

    if "libsolv" not in pkgconfig:
        oe.qa.handle_error("internal-solver-deprecation", "The opkg internal solver will be deprecated in future opkg releases. Consider enabling \"libsolv\" in PACKAGECONFIG.", d)


RDEPENDS:${PN} = "${VIRTUAL-RUNTIME_update-alternatives} opkg-arch-config libarchive"
RDEPENDS:${PN}:class-native = ""
RDEPENDS:${PN}:class-nativesdk = ""
RDEPENDS:${PN}-ptest += "make binutils python3-core python3-compression bash python3-crypt python3-io"
RREPLACES:${PN} = "opkg-nogpg opkg-collateral"
RCONFLICTS:${PN} = "opkg-collateral"
RPROVIDES:${PN} = "opkg-collateral"

FILES:libopkg = "${libdir}/*.so.* ${OPKGLIBDIR}/opkg/"

BBCLASSEXTEND = "native nativesdk"

CONFFILES:${PN} = "${sysconfdir}/opkg/opkg.conf"
