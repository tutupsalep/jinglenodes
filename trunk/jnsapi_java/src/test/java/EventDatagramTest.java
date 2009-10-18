import junit.framework.TestCase;
import org.xmpp.jnodes.nio.EventDatagramChannel;
import org.xmpp.jnodes.nio.SelDatagramChannel;
import org.xmpp.jnodes.nio.ListenerDatagramChannel;
import org.xmpp.jnodes.nio.DatagramListener;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EventDatagramTest extends TestCase {

    final static String encode = "UTF-8";
    final static String localIP = "127.0.0.1";

    public void testDatagramChannels() {

        for (int i = 0; i < 5; i++) {
            socketTest(new ChannelProvider() {
                public ListenerDatagramChannel open(DatagramListener datagramListener, SocketAddress address) throws IOException {
                    return EventDatagramChannel.open(datagramListener, address);
                }

                public String getName() {
                    return "EventDatagramChannel";
                }
            });

            socketTest(new ChannelProvider() {
                public ListenerDatagramChannel open(DatagramListener datagramListener, SocketAddress address) throws IOException {
                    return SelDatagramChannel.open(datagramListener, address);
                }

                public String getName() {
                    return "SelDatagramChannel";
                }
            });
        }
    }

    public void socketTest(final ChannelProvider provider) {
        try {

            int num = 10;
            int packets = 50;
            int tests = 100;
            final List<TestSocket> cs = new ArrayList<TestSocket>();

            for (int i = 0, j = 0; i < num; i++, j++) {
                for (int t = 0; t < 10; t++) {
                    try {
                        final TestSocket s = new TestSocket(localIP, 50000 + j, provider);
                        cs.add(s);
                        break;
                    } catch (BindException e) {
                        j++;
                    }
                }
            }

            long tTime = 0;
            long min = 1000;
            long max = 0;

            for (int h = 0; h < tests; h++) {

                final long start = System.currentTimeMillis();

                for (int ii = 0; ii < packets; ii++)
                    for (int i = 0; i < num; i++) {
                        final TestSocket a = cs.get(i);
                        final TestSocket b = i == num - 1 ? cs.get(0) : cs.get(i + 1);
                        a.getChannel().send(b.getExpectedBuffer().duplicate(), b.getAddress());
                    }

                boolean finished = false;
                while (!finished) {
                    Thread.sleep(1);
                    finished = true;
                    for (int i = 0; i < num; i++) {
                        finished &= cs.get(i).getI().get() == packets;
                    }
                }

                final long d = (System.currentTimeMillis() - start);
                if (d > max) max = d;
                if (d < min) min = d;
                tTime += d;

                for (final TestSocket ts : cs)
                    ts.getI().set(0);
            }

            System.out.println(provider.getName() + " -> Max: " + max + "ms, Min: " + min + "ms, Avg: " + Math.ceil(tTime / tests) + "ms");

            for (final TestSocket ts : cs) {
                ts.getChannel().close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public interface ChannelProvider {
        public ListenerDatagramChannel open(DatagramListener datagramListener, SocketAddress address) throws IOException;

        public String getName();
    }

    public static class TestSocket {

        final private String msg;
        final private byte[] b;
        final private AtomicInteger i;
        final private SocketAddress address;
        final private ListenerDatagramChannel channel;
        final private ByteBuffer expectedBuffer;

        public TestSocket(final String localIP, final int port, final ChannelProvider provider) throws IOException {
            msg = String.valueOf(Math.random() * 10);
            b = msg.getBytes(encode);
            expectedBuffer = ByteBuffer.wrap(b);
            i = new AtomicInteger(0);
            address = new InetSocketAddress(localIP, port);

            final DatagramListener dl = new DatagramListener() {
                public void datagramReceived(final ListenerDatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) {
                    final byte[] bt = new byte[b.length];
                    final int aux = buffer.position();
                    buffer.rewind();
                    buffer.get(bt, 0, aux);
                    if (Arrays.equals(bt, b)) {
                        i.incrementAndGet();
                    } else {
                        System.out.println("Invalid Buffer Content.");
                    }
                }
            };

            channel = provider.open(dl, address);
        }

        public AtomicInteger getI() {
            return i;
        }

        public String getMsg() {
            return msg;
        }

        public ListenerDatagramChannel getChannel() {
            return channel;
        }

        public ByteBuffer getExpectedBuffer() {
            return expectedBuffer;
        }

        public SocketAddress getAddress() {
            return address;
        }
    }
}