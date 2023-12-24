import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@Slf4j
public class JvmDemo extends ClassLoader {

/*    public static void main(String[] args) throws IOException {
*//*        new JvmDemo().run(args[0]);*//*

    }*/

    public void run(String path) throws IOException {
        List<Inst> insts = parseInst(path + ".class");

        Map<Integer, Inst> instructions = genInstructions(insts);

        Frame frame = new Frame(10, 10);
        while (true) {
            int pc = frame.pc;
            Inst inst = instructions.get(pc);
            if (inst == null) {
                break;
            }
            inst.execute(frame);
            if (pc == frame.pc) {
                frame.pc += inst.offset();
            }
        }
    }

    public static List<Inst> parseInst(String path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(path));
             BufferedInputStream bis = new BufferedInputStream(inputStream);
             DataInputStream is = new DataInputStream(bis)) {
            {
                int magic = is.readInt();
                String magicString = Integer.toHexString(magic);
                log.debug("魔数：{}", magicString);
                int minorVersion = is.readUnsignedShort();
                int majorVersion = is.readUnsignedShort();

                log.debug("副版本号：{}",minorVersion);
                log.debug("主版本号：{}",majorVersion);

                int cpSize = is.readUnsignedShort();
                for (int i = 0; i < cpSize - 1; i++) {
                    int tag = is.readUnsignedByte();
                    if (tag == 1) {
                        int size = is.readUnsignedShort();
                        for (int i1 = 0; i1 < size; i1++) {
                            is.readUnsignedByte();
                        }
                        continue;
                    }
                    if (tag == 10 || tag == 9 || tag == 12) {
                        for (int i1 = 0; i1 < 4; i1++) {
                            is.readUnsignedByte();
                        }
                        continue;
                    }
                    if (tag == 7) {
                        for (int i1 = 0; i1 < 2; i1++) {
                            is.readUnsignedByte();
                        }
                        continue;
                    }

                    throw new IllegalStateException();
                }

                int accessFlag = is.readUnsignedShort();
                int thisClass = is.readUnsignedShort();
                int superClass = is.readUnsignedShort();

                // read interfaces, ignore
                is.readUnsignedShort();
                // read field, ignore
                is.readUnsignedShort();

                is.readUnsignedShort();
                log.debug("output");
                // read <init>, ignore
                is.readUnsignedShort();
                is.readUnsignedShort();
                is.readUnsignedShort();
                is.readUnsignedShort();
                is.readUnsignedShort();
                int l1 = is.readInt();
                for (int i = 0; i < l1; i++) {
                    is.readUnsignedByte();
                }
            }
            // core, read main method
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readUnsignedShort();
            is.readInt();
            is.readUnsignedShort();
            is.readUnsignedShort();
            int codeLen = is.readInt();


            int len = codeLen;
            List<Inst> insts = new ArrayList<>();
            Inst inst = null;
            while (len > 0) {
                int code = is.readUnsignedByte();
                switch (code) {
                    case 0x03:
                        log.debug("iConst_0");
                        inst = new IConst0();
                        break;
                    case 0x04:
                        log.debug("iConst_1");
                        inst = new IConst1();
                        break;
                    case 0x05:
                        log.debug("iConst_2");
                        inst = new IConst2();
                        break;
                    case 0x12:
                        log.debug("ldc");
                        inst = new ldc(is.readUnsignedByte());
                        break;
                    case 0x3c:
                        log.debug("iStore_1");
                        inst = new IStore1();
                        break;
                    case 0x4c:
                        log.debug("aStore_1");
                        inst = new AStore1();
                        break;
                    case 0x3d:
                        log.debug("iStore_2");
                        inst = new IStore2();
                        break;
                    case 0x10:
                        log.debug("Bipush");
                        inst = new Bipush(is.readByte());
                        break;
                    case 0xa3:
                        log.debug("IfIcmpGt");
                        inst = new IfIcmpGt(is.readShort());
                        break;
                    case 0x60:
                        log.debug("Iadd");
                        inst = new Iadd();
                        break;
                    case 0x84:
                        log.debug("Iinc");
                        inst = new Iinc(is.readUnsignedByte(), is.readByte());
                        break;
                    case 0xa7:
                        log.debug("Goto");
                        inst = new Goto(is.readShort());
                        break;
                    case 0x1b:
                        log.debug("ILoad1");
                        inst = new ILoad1();
                        break;
                    case 0x2b:
                        log.debug("ILoad1");
                        inst = new ILoad_1();
                        break;
                    case 0x19:
                        log.debug("aLoad1");
                        inst = new ALoad1();
                        break;
                    case 0x1c:
                        log.debug("iLoad2");
                        inst = new ILoad2();
                        break;
                    case 0xb1:
                        log.debug("return");
                        inst = new Return();
                        break;
                    case 0xb2:
                        log.debug("getStatic");
                        is.readUnsignedShort();
                        inst = new Getstatic();
                        break;
                    case 0xb6:
                        log.debug("Invokevirtual");
                        is.readUnsignedShort();
                        inst = new Invokevirtual();
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
                len -= inst.offset();
                insts.add(inst);
            }

            return insts;
        }
    }

    private static Map<Integer, Inst> genInstructions(List<Inst> insts) {
        Map<Integer, Inst> map = new LinkedHashMap<>(insts.size());
        int i = 0;
        for (Inst inst : insts) {
            map.put(i, inst);
            i += inst.offset();
        }
        return map;
    }


}

interface Inst {
    default int offset() {
        return 1;
    }

    void execute(Frame frame);

}

class IStore1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.localVars[1] = frame.operandStack.pop();
    }
}
class AStore1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.localVars[1] = frame.operandStack.pop();
    }
}

class IStore2 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.localVars[2] = frame.operandStack.pop();
    }
}

class ILoad1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(frame.localVars[1]);
    }
}
class ILoad_1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(frame.localVars[1]);
    }
}
class ALoad1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(frame.localVars[1]);
    }
}
class ILoad2 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(frame.localVars[2]);
    }
}
class ldc implements Inst {

    private int index;  // The constant pool index

    public ldc(int index) {
        this.index = index;
    }

    @Override
    public void execute(Frame frame) {
        // Assuming frame.getConstant(index) returns the constant from the constant pool at the given index
        Object localVar = frame.localVars[index];

        // Push the constant onto the operand stack
        frame.operandStack.push(localVar);
    }
}


class Bipush implements Inst {

    final int val;
    Bipush(int val) {
        this.val = val;
    }

    @Override
    public int offset() {
        return 2;
    }

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(val);
    }

}

class IfIcmpGt implements Inst {

    final int offset;

    IfIcmpGt(int offset) {
        this.offset = offset;
    }

    @Override
    public int offset() {
        return 3;
    }

    @Override
    public void execute(Frame frame) {
        Integer val2 = (Integer) frame.operandStack.pop();
        Integer val1 = (Integer) frame.operandStack.pop();
        if (val1 > val2) {
            frame.pc += offset;
        }
    }
}

class Iadd implements Inst {

    @Override
    public void execute(Frame frame) {
        Integer val2 = (Integer) frame.operandStack.pop();
        Integer val1 = (Integer) frame.operandStack.pop();
        frame.operandStack.push(val1 + val2);
    }
}

class Iinc implements Inst {

    final int index;
    final int val;

    Iinc(int index, int val) {
        this.index = index;
        this.val = val;
    }

    @Override
    public int offset() {
        return 3;
    }

    @Override
    public void execute(Frame frame) {
        Integer tmp = ((Integer) frame.localVars[index]);
        tmp += val;
        frame.localVars[index] = tmp;
    }
}

class IConst0 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(0);
    }
}

class IConst1 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(1);
    }
}
class IConst2 implements Inst {

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(2);
    }
}

class Goto implements Inst {
    final int offset;

    Goto(int offset) {
        this.offset = offset;
    }

    @Override
    public int offset() {
        return 3;
    }

    @Override
    public void execute(Frame frame) {
        frame.pc += offset;
    }
}

class Return implements Inst {

    @Override
    public void execute(Frame frame) {
        // do nothings
    }
}

class Getstatic implements Inst {

    @Override
    public int offset() {
        return 3;
    }

    @Override
    public void execute(Frame frame) {
        frame.operandStack.push(null);
    }
}

class Invokevirtual implements Inst {

    @Override
    public int offset() {
        return 3;
    }

    @Override
    public void execute(Frame frame) {
        Object val = frame.operandStack.pop();
        Object thisObj = frame.operandStack.pop();
        System.out.println(val);
    }
}

class Frame {

    public final Object[] localVars;
    public final Stack<Object> operandStack;
    public int pc = 0;

    public Frame(int locals, int stacks) {
        this.localVars = new Object[locals];
        this.operandStack = new Stack<>();
    }
}