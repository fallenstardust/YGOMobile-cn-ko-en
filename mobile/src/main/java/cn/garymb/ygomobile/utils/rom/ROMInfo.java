package cn.garymb.ygomobile.utils.rom;

/**
 * <pre>
 *    author : Senh Linsh
 *    github : https://github.com/SenhLinsh
 *    date   : 2018/07/03
 *    desc   :
 * </pre>
 */
public class ROMInfo {
    private ROM rom;
    private int baseVersion;
    private String version;

    public ROMInfo(ROM rom) {
        this.rom = rom;
    }

    public ROMInfo(ROM rom, int baseVersion, String version) {
        this.rom = rom;
        this.baseVersion = baseVersion;
        this.version = version;
    }

    public void setRom(ROM rom) {
        this.rom = rom;
    }

    public void setBaseVersion(int baseVersion) {
        this.baseVersion = baseVersion;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ROM getRom() {

        return rom;
    }

    public int getBaseVersion() {
        return baseVersion;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        if(version != null){
            return rom+"/"+version;
        }
        return String.valueOf(rom);
    }
}
