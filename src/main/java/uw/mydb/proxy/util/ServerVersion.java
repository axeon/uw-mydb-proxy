package uw.mydb.proxy.util;

/**
 * MySQL 服务器版本号封装，解析形如 "8.0.30" 或 "8.0.30-0ubuntu0.20.04.1" 的版本字符串为 major.minor.subMinor。
 * 实现 {@link Comparable}，用于根据版本差异选择不同加密策略（如 caching_sha2_password 的 RSA padding）。
 */
public class ServerVersion implements Comparable<ServerVersion> {
    /**
     * 原始完整版本字符串（可能包含发行版后缀），toString 时优先返回。
     */
    private String completeVersion;
    /**
     * 主版本号。
     */
    private Integer major;
    /**
     * 次版本号。
     */
    private Integer minor;
    /**
     * 修订号。
     */
    private Integer subMinor;

    /**
     * @param completeVersion 原始完整版本字符串
     * @param major           主版本号
     * @param minor           次版本号
     * @param subMinor        修订号
     */
    public ServerVersion(String completeVersion, int major, int minor, int subMinor) {
        this.completeVersion = completeVersion;
        this.major = major;
        this.minor = minor;
        this.subMinor = subMinor;
    }

    /**
     * @param major    主版本号
     * @param minor    次版本号
     * @param subMinor 修订号
     */
    public ServerVersion(int major, int minor, int subMinor) {
        this(null, major, minor, subMinor );
    }

    /**
     * 解析版本字符串为 major/minor/subminor。无法解析时返回 (0,0,0)。
     *
     * @param versionString 字符串版本表示
     * @return {@link ServerVersion}
     */
    public static ServerVersion parseVersion(final String versionString) {
        int point = versionString.indexOf('.');

        if (point != -1) {
            try {
                int serverMajorVersion = Integer.parseInt(versionString.substring(0, point));

                String remaining = versionString.substring(point + 1, versionString.length());
                point = remaining.indexOf('.');

                if (point != -1) {
                    int serverMinorVersion = Integer.parseInt(remaining.substring(0, point));

                    remaining = remaining.substring(point + 1, remaining.length());

                    int pos = 0;

                    while (pos < remaining.length()) {
                        if ((remaining.charAt(pos) < '0') || (remaining.charAt(pos) > '9')) {
                            break;
                        }

                        pos++;
                    }

                    int serverSubminorVersion = Integer.parseInt(remaining.substring(0, pos));

                    return new ServerVersion(versionString, serverMajorVersion, serverMinorVersion, serverSubminorVersion);
                }
            } catch (NumberFormatException NFE1) {
            }
        }

        // can't parse the server version
        return new ServerVersion(0, 0, 0);
    }

    public int getMajor() {
        return this.major;
    }

    public int getMinor() {
        return this.minor;
    }

    public int getSubMinor() {
        return this.subMinor;
    }

    /**
     * A string representation of this version. If this version was parsed from, or provided with, a "complete" string which may contain more than just the
     * version number, this string is returned verbatim. Otherwise, a string representation of the version numbers is given.
     *
     * @return string version representation
     */
    @Override
    public String toString() {
        if (this.completeVersion != null) {
            return this.completeVersion;
        }
        return String.format("%d.%d.%d", this.major, this.minor, this.subMinor );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !ServerVersion.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        ServerVersion another = (ServerVersion) obj;
        if (this.getMajor() != another.getMajor() || this.getMinor() != another.getMinor() || this.getSubMinor() != another.getSubMinor()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 23;
        hash += 19 * hash + this.major;
        hash += 19 * hash + this.minor;
        hash += 19 * hash + this.subMinor;
        return hash;
    }

    public int compareTo(ServerVersion other) {
        int c;
        if ((c = this.major.compareTo(other.getMajor())) != 0) {
            return c;
        } else if ((c = this.minor.compareTo(other.getMinor())) != 0) {
            return c;
        }
        return this.subMinor.compareTo(other.getSubMinor());
    }

    /**
     * Does this version meet the minimum specified by `min'?
     *
     * @param min The minimum version to compare against.
     * @return true if version meets the minimum specified by `min'
     */
    public boolean meetsMinimum(ServerVersion min) {
        return compareTo(min) >= 0;
    }
}
