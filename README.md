## What this recipe builds

The recipe builds and packages **NXP Plug & Trust Middleware** (also referred to as “SE05X Plug & Trust MW”) for an i.MX Linux target.

It uses CMake to compile the middleware and then installs:

- A copy of the (patched/modified) Plug & Trust package tree under `/root/<mw-root-name>/`
- Selected shared libraries into `/usr/local/lib`
- Environment/profile setup for `ssscli` under `/etc/profile.d`
- An `ld.so` configuration snippet under `/etc/ld.so.conf.d`
- A configuration file under `/pixel/auth`
- An additional prebuilt runtime environment tarball extracted into `/root/ssscli_env`

## Where the sources come from

The recipe pulls sources from two places:

1. **A local zip file** in the layer:
   - `SRC_URI` includes: `file://${NXP-PLUG-TRUST-MW}.zip`
   - The zip root name must match the `NXP-PLUG-TRUST-MW` variable exactly.

2. **An external git repository** for an extra tarball:
   - `SRC_URI` includes a `git://...mb8-a53-tools.git;protocol=ssh;...;name=extratgz`
   - The recipe then expects `${EXTRATGZ_DIR}/ssscli_env.tgz` and extracts it into `/root`

Notes:

- The git fetch uses SSH (`protocol=ssh`) and therefore requires credentials/keys at build time.
- `SRCREV_extratgz = "${AUTOREV}"` tracks the latest commit, which makes builds non-reproducible unless you pin `SRCREV_extratgz` to a fixed revision.

## Key variables

### `NXP-PLUG-TRUST-MW`

```
NXP-PLUG-TRUST-MW = "SE-PLUG-TRUST-MW_04.07.00"
```

This value is used as:

- The expected filename base of the zip in `SRC_URI` (`<value>.zip`)
- The directory name used during install (`/root/<value>/`)

If you update the middleware version (new zip), update this variable to match the exact root name of the zip.

### Work directories

- `S = "${WORKDIR}/simw-top"`: source tree root after unpacking
- `B = "${WORKDIR}/build"`: out-of-tree build directory
- `EXTRATGZ_DIR = "${WORKDIR}/git"`: where the `extratgz` git fetch is checked out

## Build system and configuration

The recipe inherits:

- `cmake`: but it overrides `do_configure()` and calls `cmake` directly
- `dos2unix`: available if line-ending normalization is needed (not explicitly invoked in the recipe body)

### Dependencies

Build-time (`DEPENDS`):

- `openssl`
- `python3`
- `systemd`
- `python3-virtualenv`

Runtime (`RDEPENDS:${PN}`):

- `bash`
- `zsh`
- `python3-misc`
- `python3-virtualenv`

### CMake flags

`do_configure()` invokes `cmake` with many `-D` options to select:

- Host platform (`PTMW_Host=iMXLinux`)
- Crypto backend (`PTMW_HostCrypto=OPENSSL`, `PTMW_OpenSSL=3_0`)
- Secure element + transport settings (`PTMW_Applet=SE05X_C`, `PTMW_SMCOM=T1oI2C`, etc.)
- Feature flags for SSS/SE05X and software crypto counterparts (`SSSFTR_*`)

If you need a different transport, auth mode, or feature set, update the corresponding `-D` flags in `do_configure()`.

## Install and packaging behavior

### `do_install:append()`

The install step is opinionated and does more than the usual “install built artifacts”:

1. Copies the entire (unzipped and possibly patched) package directory into:
   - `/root/${NXP-PLUG-TRUST-MW}`

2. Removes selected directories/files from that copy to reduce size and remove host-only artifacts, e.g.:

- `simw-top/binaries/ex`
- `simw-top/binaries/PCLinux64`
- `simw-top/ext/mcu-sdk`
- `simw-top/ext/openssl/lib_mingw/`
- `libsss_engine.so`, `libsssProvider.so`, `libsssapisw.so` in specific locations

3. Ensures shell scripts are executable:

- `find ... -name "*.sh" -exec chmod 755 {} \;`

4. Adds an “update installer” convenience copy:

- Copies `simw-top/scripts/imx/se05x_mw_install.sh` to `/root/install/`
- Adds a `/root/install/readme.txt`

5. Extracts an extra runtime environment:

- Untars `${EXTRATGZ_DIR}/ssscli_env.tgz` into `/root` → `/root/ssscli_env`

6. Configures dynamic linker paths:

- Writes `/etc/ld.so.conf.d/se05x.conf` containing `/usr/local/lib`

7. Adds profile setup:

- Installs `px-ssscli.sh` to `/etc/profile.d/px-ssscli.sh`

8. Installs additional runtime artifacts:

- `libcore_json.so` into `/usr/local/lib`
- `openssl30_sss_se050.cnf` into `/pixel/auth`

### Packaged files

The recipe sets `FILES:${PN}` to include:

- `/usr/local/lib/*.so`
- `/usr/local/bin/*`
- `/pixel/auth/*`
- `/root/${NXP-PLUG-TRUST-MW}`
- `/root/ssscli_env`
- `/root/install`
- `/etc/ld.so.conf.d/se05x.conf`
- `/etc/profile.d/px-ssscli.sh`
- `/usr/bin/ssscli`

And for development packages:

- `${PN}-dev`: headers, shares, CMake package files under `/usr/local/...`
- `${PN}-staticdev`: static libraries under `/usr/local/lib/*.a*`

Important:

- `FILES:${PN}` mentions `/usr/bin/ssscli`, but this recipe does not explicitly install a binary to `/usr/bin`.
  If you expect `ssscli` on `PATH`, verify where it is produced/installed and adjust `do_install()` and/or `FILES` accordingly.

## QA / packaging exceptions

The recipe relaxes some package QA checks:

- `INSANE_SKIP:${PN} += "dev-so"`: allows `.so` symlinks in the non-`-dev` package
- `INSANE_SKIP:${PN} += "libdir"`: avoids warnings about libraries under a non-standard path (e.g. `/usr/local/lib`)

These are intentional because the recipe installs to `/usr/local/lib` and may ship `.so` links outside of standard Yocto packaging conventions.

## How to update this recipe

When updating to a new Plug & Trust MW release:

1. Drop the new zip into the appropriate `files/` area for this recipe.
2. Update:
   - `NXP-PLUG-TRUST-MW` to match the new zip root name exactly.
3. Re-evaluate the CMake flags in `do_configure()` for any changes in the upstream build system.
4. Verify that the “cleanup removes” in `do_install:append()` still match the new tree layout.
5. Consider pinning:
   - `SRCREV_extratgz` to a fixed commit to keep builds reproducible.

## Operational notes

- This recipe installs a significant amount of content under `/root`, which is not typical for Yocto production images.
  If you want a more standard layout, consider moving runtime pieces to `/usr` and using `/var` for mutable data.
- Because the git source uses SSH, CI or build servers must have access to the Bitbucket repo.
