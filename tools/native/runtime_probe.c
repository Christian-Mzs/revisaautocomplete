/* Exercise operations needed by Codex startup without reading credentials. */
#include <errno.h>
#include <fcntl.h>
#include <linux/stat.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

/* Static ARM64 Bionic needs a 64-byte executable TLS alignment. Force the
 * linker to lay out TLS correctly; changing page alignment does not do this.
 * This is a real TLS object, not an ELF-header patch after linking. */
#if defined(__ANDROID__) && defined(__aarch64__)
static __thread volatile unsigned char tls_alignment __attribute__((aligned(64), used));
#endif

static void fail(const char *operation) {
    fprintf(stderr, "Operacao %s falhou: %s (errno=%d)\n", operation, strerror(errno), errno);
    exit(1);
}
#define CHECK(operation, expression) do { if (!(expression)) fail(operation); puts(operation ": OK"); } while (0)

int main(int argc, char **argv) {
#if defined(__ANDROID__) && defined(__aarch64__)
    tls_alignment = 1;
#endif
    const char *base = argc > 1 ? argv[1] : "/codex-home";
    char directory[3800], file[4096], link[4096], resolved[4096];
    if (snprintf(directory, sizeof(directory), "%s/native-probe-XXXXXX", base) >= (int)sizeof(directory)) {
        errno = ENAMETOOLONG; fail("probe path");
    }
    CHECK("mkdtemp", mkdtemp(directory) != NULL);
    snprintf(file, sizeof(file), "%s/file", directory);
    snprintf(link, sizeof(link), "%s/link", directory);
    int fd = open(file, O_CREAT | O_RDWR | O_EXCL, 0600);
    CHECK("open", fd >= 0);
    CHECK("fchmod", fchmod(fd, 0600) == 0);
    CHECK("flock", flock(fd, LOCK_EX | LOCK_NB) == 0);
    CHECK("write", write(fd, "probe", 5) == 5);
    CHECK("fsync", fsync(fd) == 0);
    CHECK("symlink", symlink("file", link) == 0);
    CHECK("realpath", realpath(link, resolved) != NULL);
    struct statx info;
    memset(&info, 0, sizeof(info));
    CHECK("statx(path)", syscall(SYS_statx, AT_FDCWD, file, 0, STATX_BASIC_STATS, &info) == 0);
    CHECK("statx(size)", info.stx_size == 5);
    memset(&info, 0, sizeof(info));
    CHECK("statx(fd)", syscall(SYS_statx, fd, "", AT_EMPTY_PATH, STATX_BASIC_STATS, &info) == 0);
    CHECK("statx(fd size)", info.stx_size == 5);
    CHECK("getcwd", getcwd(resolved, sizeof(resolved)) != NULL);
    close(fd);
    unlink(link); unlink(file); rmdir(directory);
    puts("Runtime: operacoes nativas verificadas");
    return 0;
}
