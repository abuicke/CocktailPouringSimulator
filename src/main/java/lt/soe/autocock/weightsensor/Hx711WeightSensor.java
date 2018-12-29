package lt.soe.autocock.weightsensor;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.util.Random;

public class Hx711WeightSensor {

    private static final double AVERAGE_GRAMS_PER_MILLILITRE = 0.893F;
    private static final int FLOW_RATE_MILLILITRES_PER_SECOND = 20;
    private static final double FLOW_RATE_GRAMS_PER_SECOND = AVERAGE_GRAMS_PER_MILLILITRE * (
            (double) FLOW_RATE_MILLILITRES_PER_SECOND
    );

    private static final CocktailGlass PLASTIC_COCKTAIL_GLASS = new CocktailGlass(210, 17.3);
    private static final CocktailGlass TUMBLER_COCKTAIL_GLASS = new CocktailGlass(325, 320);
    private static final CocktailGlass MARTINI_COCKTAIL_GLASS = new CocktailGlass(175, 216.67);
    private static final CocktailGlass PINA_COLADA_COCKTAIL_GLASS = new CocktailGlass(460, 303.33);
    private static final CocktailGlass SAUCER_COCKTAIL_GLASS = new CocktailGlass(200, 211);

    private static final CocktailGlass[] COCKTAIL_GLASSES = {
            PLASTIC_COCKTAIL_GLASS,
            TUMBLER_COCKTAIL_GLASS,
            MARTINI_COCKTAIL_GLASS,
            PINA_COLADA_COCKTAIL_GLASS,
            SAUCER_COCKTAIL_GLASS
    };

    public static void main(String[] args) {
        System.out.println("weight sensor initialised");
        try (ZContext context = new ZContext()) {
            while (!Thread.currentThread().isInterrupted()) {
                ZMQ.Socket replySocket = context.createSocket(ZMQ.REP);
                replySocket.bind("tcp://*:5555");
                System.out.println("reply socket bound to TCP port 5555, waiting for request from server...");
                byte[] reply = replySocket.recv(0);
                if (reply[0] == 1) {
                    System.out.println("received request from sever to start running weight sensor simulation");
                }
                System.out.println("closes reply socket, request has been received");
                replySocket.send(new byte[]{0});
                replySocket.close();

                System.out.println("opening publisher socket to begin sending weight sensor readings");
                ZMQ.Socket publisherSocket = context.createSocket(ZMQ.PUB);
                publisherSocket.bind("tcp://*:5555");
                System.out.println("publisher socket bound to TCP port 5555");

                //  Ensure subscriber connection has time to complete
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    throw new IllegalStateException(ie);
                }

                // Set initial weight to 0.0
                double currentSensorWeight = 0.0D;
                CocktailGlass cocktailGlass = getRandomCocktailGlass();

                try {
                    // Send initial weight of 0.0
                    send(publisherSocket, currentSensorWeight);
                    System.out.println("sent initial weight reading 0.0");

                    System.out.println("placing empty glass on the weight sensor");
                    // Wait between 1 and 5 seconds to place the glass on the sensor.
                    Thread.sleep(1000 + (1000 * new Random().nextInt(5)));
                    System.out.println("empty glass has been placed on the weight sensor");

                    // Put the glass on the weight sensor.
                    currentSensorWeight += cocktailGlass.weightGrams;
                    send(publisherSocket, currentSensorWeight);
                    System.out.println("sent simulated weight reading " +
                            currentSensorWeight + " of empty glass placed on sensor");
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                // Fill the glass to 95% capacity.
                double volumeMillilitres = ((double) cocktailGlass.volumeMillilitres) * 0.95D;
                // Get the weight in grams of the liquid when it fills the glass
                double gramsOfLiquid = volumeMillilitres * AVERAGE_GRAMS_PER_MILLILITRE;
                double finalWeight = currentSensorWeight + gramsOfLiquid;

                System.out.println("filling glass...");
                while (currentSensorWeight < finalWeight) {
                    // Get flow rate per tenth of a second.
                    currentSensorWeight += (FLOW_RATE_GRAMS_PER_SECOND * 0.1);
//                    System.out.println("sending updated weight detected by " +
//                            "sensor as glass is being filled, " + currentSensorWeight);
                    send(publisherSocket, currentSensorWeight);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
                send(publisherSocket, -1);
                System.out.println("glass has been filled");
                System.out.println("closing socket to server...");
                publisherSocket.close();
                System.out.println("socket has been closed");
                System.out.println("weight sensor simulator shutting down...");
//            System.exit(0);
            }
        }
    }

    private static CocktailGlass getRandomCocktailGlass() {
        int randomIndex = new Random().nextInt(COCKTAIL_GLASSES.length);
        return COCKTAIL_GLASSES[randomIndex];
    }

    private static void send(ZMQ.Socket zeroMqSocket, double updatedSensorWeight) {
        byte[] bytes = ByteBuffer.allocate(8).putDouble(updatedSensorWeight).array();
        zeroMqSocket.send("weight sensor topic", ZMQ.SNDMORE);
        zeroMqSocket.send(bytes);
    }

}
