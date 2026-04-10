# catfuscator

jvm bytecode obfuscator, see [Configuration.kt](./utils/src/main/kotlin/xyz/qweru/cat/config/Configuration.kt) for usage

## Showcase (outdated)
- Before obfuscation
  ```java
  private static void someVarargs(Object object, Object ... objects) {
        System.out.print("first object = ");
        System.out.print(object);
        System.out.println();
        for (int i = 0; i < objects.length; ++i) {
            System.out.print("Object ");
            System.out.print(i);
            System.out.print(" = ");
            System.out.print(objects[i]);
            System.out.println();
        }
    }
  ```
- After obfuscation (slightly cleaned up for readability)
  ```java
    private static void method0(Object field1, Object... field2) {
      int var10000 = ~((class3.field0[87] | -1) & ~(class3.field0[87] & -1) & (class3.field0[88] | -1) & ~(class3.field0[88] & -1)) & -1
         | (class3.field0[87] | -1) & ~(class3.field0[87] & -1) & (class3.field0[88] | -1) & ~(class3.field0[88] & -1) & ~-1;

      while (true) {
         switch (var10000) {
            case 1176273:
               PrintStream var6 = System.out;
               class1 var9 = new class1(
                  ~(~class3.field1[67] | ~class3.field1[68]),
                  ~(~class3.field1[69] | ~class3.field1[70]),
                  class3.field1[71] ^ class3.field1[72],
                  ~(~class3.field1[73] | ~class3.field1[74])
               );
               String var11 = (String)class3.field2[28];
               int var14 = ~((class3.field0[119] | -1) & ~(class3.field0[119] & -1) | (class3.field0[120] | -1) & ~(class3.field0[120] & -1)) & -1
                  | ((class3.field0[119] | -1) & ~(class3.field0[119] & -1) | (class3.field0[120] | -1) & ~(class3.field0[120] & -1)) & ~-1;
               int var15 = (Integer)class0.method0((String)class3.field2[29], new Object[0], "gV��$$n�\u001a7����Dx,j\u0019\f=\u0000�@�R����I�");
               int var18 = (Integer)class0.method0((String)class3.field2[30], new Object[0], "gV��$$n�\u001a7����Dx,j\u0019\f=\u0000�@�R����I�");
               Object[] var19 = new Object[]{
                  null,
                  null,
                  ((~var15 & -1 | var15 & ~-1) & (var18 | -1) & ~(var18 & -1) | -1) & ~((~var15 & -1 | var15 & ~-1) & (var18 | -1) & ~(var18 & -1) & -1)
               };
               var19[1] = var14;
               var19[0] = var11;
               class0.method0(
                  var6,
                  new Object[]{(String)class0.method0(var9, var19, "�bu\u001f��\u000e\u0003�I����r�\u001bl\u0016ZW�h��Ss\u0017G�\u0019")},
                  "\f�\r�I�\r,g�r�2Em���\u0015j,��0�̹y�<��"
               );
               var10000 = ~(~class3.field0[121] & -1 | class3.field0[121] & ~-1 | (class3.field0[122] | -1) & ~(class3.field0[122] & -1)) & -1
                  | (~class3.field0[121] & -1 | class3.field0[121] & ~-1 | (class3.field0[122] | -1) & ~(class3.field0[122] & -1)) & ~-1;
               break;
            case 2352546:
               class0.method0(System.out, new Object[0], "5�*8�h�{��%\u001a3f�\u001e06t�Q���\u0010?~h\f�\u001b");
               var10000 = ~((~class3.field0[117] & -1 | class3.field0[117] & ~-1) & (~class3.field0[118] & -1 | class3.field0[118] & ~-1)) & -1
                  | (~class3.field0[117] & -1 | class3.field0[117] & ~-1) & (~class3.field0[118] & -1 | class3.field0[118] & ~-1) & ~-1;
               break;
            case 4705092:
               class0.method0(System.out, new Object[]{field1}, "�E�D�w�~I\u007f\u0016��,Z\u001d0�\"[!����w㥮�\u0019�");
               var10000 = ((class3.field0[105] | -1) & ~(class3.field0[105] & -1) | ~class3.field0[106] & -1 | class3.field0[106] & ~-1 | -1)
                  & ~(((class3.field0[105] | -1) & ~(class3.field0[105] & -1) | ~class3.field0[106] & -1 | class3.field0[106] & ~-1) & -1);
               break;
            case 5881365:
               int field0 = (class3.field0[97] | class3.field0[98]) & ~(class3.field0[97] & class3.field0[98]);
               var10000 = ((class3.field0[99] | -1) & ~(class3.field0[99] & -1) | ~class3.field0[100] & -1 | class3.field0[100] & ~-1 | -1)
                  & ~(((class3.field0[99] | -1) & ~(class3.field0[99] & -1) | ~class3.field0[100] & -1 | class3.field0[100] & ~-1) & -1);

               while (true) {
                  switch (var10000) {
                     case 1287616:
                        class0.method0(System.out, new Object[]{field0}, ";�\u007f�>\u061cڐ���i�s��9�^��\u000b�/܅2��U");
                        var10000 = ((class3.field0[111] | -1) & ~(class3.field0[111] & -1) & (~class3.field0[112] & -1 | class3.field0[112] & ~-1) | -1)
                           & ~((class3.field0[111] | -1) & ~(class3.field0[111] & -1) & (~class3.field0[112] & -1 | class3.field0[112] & ~-1) & -1);
                        break;
                     case 2575232:
                        PrintStream var5 = System.out;
                        class1 var8 = new class1(
                           ~(~class3.field1[51] | ~class3.field1[52]),
                           ~(~class3.field1[53] | ~class3.field1[54]),
                           class3.field1[55] ^ class3.field1[56],
                           ~(~class3.field1[57] | ~class3.field1[58])
                        );
                        String var10 = (String)class3.field2[24];
                        int var13 = ~((class3.field0[89] | -1) & ~(class3.field0[89] & -1) | ~class3.field0[90] & -1 | class3.field0[90] & ~-1) & -1
                           | ((class3.field0[89] | -1) & ~(class3.field0[89] & -1) | ~class3.field0[90] & -1 | class3.field0[90] & ~-1) & ~-1;
                        Object[] var17 = new Object[]{
                           null,
                           null,
                           ~(~class3.field0[91] & -1 | class3.field0[91] & ~-1 | ~class3.field0[92] & -1 | class3.field0[92] & ~-1) & -1
                              | (~class3.field0[91] & -1 | class3.field0[91] & ~-1 | ~class3.field0[92] & -1 | class3.field0[92] & ~-1) & ~-1
                        };
                        var17[1] = var13;
                        var17[0] = var10;
                        class0.method0(
                           var5,
                           new Object[]{(String)class0.method0(var8, var17, "�bu\u001f��\u000e\u0003�I����r�\u001bl\u0016ZW�h��Ss\u0017G�\u0019")},
                           "\f�\r�I�\r,g�r�2Em���\u0015j,��0�̹y�<��"
                        );
                        var10000 = ~((~class3.field0[93] & -1 | class3.field0[93] & ~-1) & (class3.field0[94] | -1) & ~(class3.field0[94] & -1)) & -1
                           | (~class3.field0[93] & -1 | class3.field0[93] & ~-1) & (class3.field0[94] | -1) & ~(class3.field0[94] & -1) & ~-1;
                        break;
                     case 3862848:
                        class0.method0(System.out, new Object[0], "5�*8�h�{��%\u001a3f�\u001e06t�Q���\u0010?~h\f�\u001b");
                        var10000 = ~((class3.field0[95] | -1) & ~(class3.field0[95] & -1) | (class3.field0[96] | -1) & ~(class3.field0[96] & -1)) & -1
                           | ((class3.field0[95] | -1) & ~(class3.field0[95] & -1) | (class3.field0[96] | -1) & ~(class3.field0[96] & -1)) & ~-1;
                        break;
                     case 6438080:
                        PrintStream var4 = System.out;
                        class1 var7 = new class1(
                           class3.field1[59] ^ class3.field1[60],
                           ~(~class3.field1[61] | ~class3.field1[62]),
                           class3.field1[63] ^ class3.field1[64],
                           class3.field1[65] ^ class3.field1[66]
                        );
                        String var10002 = (String)class3.field2[25];
                        int var12 = ~(~class3.field0[113] & -1 | class3.field0[113] & ~-1 | (class3.field0[114] | -1) & ~(class3.field0[114] & -1)) & -1
                           | (~class3.field0[113] & -1 | class3.field0[113] & ~-1 | (class3.field0[114] | -1) & ~(class3.field0[114] & -1)) & ~-1;
                        int var10004 = (Integer)class0.method0((String)class3.field2[26], new Object[0], "gV��$$n�\u001a7����Dx,j\u0019\f=\u0000�@�R����I�");
                        int var10005 = (Integer)class0.method0((String)class3.field2[27], new Object[0], "gV��$$n�\u001a7����Dx,j\u0019\f=\u0000�@�R����I�");
                        Object[] var16 = new Object[]{
                           null,
                           null,
                           ~((var10004 | -1) & ~(var10004 & -1) & (var10005 | -1) & ~(var10005 & -1)) & -1
                              | (var10004 | -1) & ~(var10004 & -1) & (var10005 | -1) & ~(var10005 & -1) & ~-1
                        };
                        var16[1] = var12;
                        var16[0] = var10002;
                        class0.method0(
                           var4,
                           new Object[]{(String)class0.method0(var7, var16, "�bu\u001f��\u000e\u0003�I����r�\u001bl\u0016ZW�h��Ss\u0017G�\u0019")},
                           "\f�\r�I�\r,g�r�2Em���\u0015j,��0�̹y�<��"
                        );
                        var10000 = ~((~class3.field0[115] & -1 | class3.field0[115] & ~-1) & (class3.field0[116] | -1) & ~(class3.field0[116] & -1)) & -1
                           | (~class3.field0[115] & -1 | class3.field0[115] & ~-1) & (class3.field0[116] | -1) & ~(class3.field0[116] & -1) & ~-1;
                        break;
                     case 7725696:
                        class0.method0(System.out, new Object[]{field2[field0]}, "�E�D�w�~I\u007f\u0016��,Z\u001d0�\"[!����w㥮�\u0019�");
                        var10000 = ~class3.field0[107] & class3.field0[108] | class3.field0[107] & ~class3.field0[108];
                        break;
                     case 9013312:
                     case 11588544:
                        int var10001 = field2.length;
                        int var10003 = ~((~class3.field0[101] & -1 | class3.field0[101] & ~-1) & (~class3.field0[102] & -1 | class3.field0[102] & ~-1)) & -1
                           | (~class3.field0[101] & -1 | class3.field0[101] & ~-1) & (~class3.field0[102] & -1 | class3.field0[102] & ~-1) & ~-1;
                        if (field0 >= var10001) {
                           switch (var10003) {
                              case 1036472:
                                 return;
                              default:
                                 throw null;
                           }
                        }

                        var10000 = ((class3.field0[103] | -1) & ~(class3.field0[103] & -1) & (class3.field0[104] | -1) & ~(class3.field0[104] & -1) | -1)
                           & ~((class3.field0[103] | -1) & ~(class3.field0[103] & -1) & (class3.field0[104] | -1) & ~(class3.field0[104] & -1) & -1);
                        break;
                     case 12876160:
                        field0++;
                        var10000 = (~class3.field0[109] & -1 | class3.field0[109] & ~-1 | ~class3.field0[110] & -1 | class3.field0[110] & ~-1 | -1)
                           & ~((~class3.field0[109] & -1 | class3.field0[109] & ~-1 | ~class3.field0[110] & -1 | class3.field0[110] & ~-1) & -1);
                        break;
                     default:
                        throw null;
                  }
               }
            default:
               throw null;
         }
      }
   }
  ```
