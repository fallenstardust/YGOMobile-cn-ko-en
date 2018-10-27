package tests;

public class A {

    /*@Test
    public void testVersion() {
        System.out.println(getVersionString(0x233E));
        System.out.println(getVersionString(0xF99F));
        System.out.println(getVersionValue("0x2099F"));
        System.out.println(getVersionValue("0xF99F"));
        System.out.println(getVersionValue("1.034.00"));
        System.out.println(getVersionValue("1.034.0"));
        System.out.println(getVersionString(4928));
    }*/

    public String getVersionString(int value) {
        int last = (value & 0xf);
        int m = ((value >> 4) & 0xff);
        int b = ((value >> 12) & 0xff);
        return String.format("%X.%03X.%X", b, m, last);
    }

    /*public int getVersionValue(String str) {
        str = str.trim().toLowerCase(Locale.US);
        int v = -1;
        if(str.contains(".")){
            String[] vas = str.split("\\.");
            if(vas.length<3){
                return -1;
            }
            try {
                int last = Integer.parseInt(vas[2]);
                int m = Integer.parseInt(vas[1])<<4;
                int b = Integer.parseInt(vas[0])<<12;
                v = last+m+b;
            }catch (Exception e){

            }
        }else{
            try {
                if (str.startsWith("0x")) {
                    str = str.substring(2);
                }
                v = Integer.parseInt(str, 16);
            } catch (Exception e) {
            }
        }
        return v;
    }*/
}