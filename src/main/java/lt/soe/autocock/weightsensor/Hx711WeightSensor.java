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
        System.out.println("weight sensor started...");
        try (ZContext context = new ZContext()) {
            ZMQ.Socket zeroMqSocket = context.createSocket(ZMQ.PUB);
            zeroMqSocket.bind("tcp://*:5555");
            System.out.println("created ZeroMQ reply socket on localhost TCP port 5555");

            double currentSensorWeight = 0.0D;
            CocktailGlass cocktailGlass = getRandomCocktailGlass();

            try {
                byte[] reply = zeroMqSocket.recv(0);
                System.out.println("received request code, weight sensor running...");

                // Send initial weight 0.
                send(zeroMqSocket, currentSensorWeight);
                System.out.println("send initial weight reading 0.0");

                reply = zeroMqSocket.recv(0);

                // Wait between 1 and 5 seconds to place the glass on the sensor.
                Thread.sleep(1000 + (1000 * new Random().nextInt(5)));

                // Put the glass on the weight sensor.
                currentSensorWeight += cocktailGlass.weightGrams;
                send(zeroMqSocket, currentSensorWeight);
                System.out.println("send simulated weight reading " +
                        currentSensorWeight + " (empty glass put on sensor)");
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }

            System.out.println("filling glass...");

            // Fill the glass to 95% capacity.
            double volumeMillilitres = ((double) cocktailGlass.volumeMillilitres) * 0.95D;
            // Get the weight in grams of the liquid when it fills the glass
            double gramsOfLiquid = volumeMillilitres * AVERAGE_GRAMS_PER_MILLILITRE;
            double finalWeight = currentSensorWeight + gramsOfLiquid;

            while (currentSensorWeight < finalWeight) {
                byte[] reply = zeroMqSocket.recv(0);
                // Get flow rate per tenth of a second.
                currentSensorWeight += (FLOW_RATE_GRAMS_PER_SECOND * 0.1);
                send(zeroMqSocket, currentSensorWeight);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }

            main(null);
        }
    }

    private static CocktailGlass getRandomCocktailGlass() {
        int randomIndex = new Random().nextInt(COCKTAIL_GLASSES.length);
        return COCKTAIL_GLASSES[randomIndex];
    }

    private static void send(ZMQ.Socket zeroMqSocket, double updatedSensorWeight) {
        byte[] bytes = ByteBuffer.allocate(8).putDouble(updatedSensorWeight).array();
        zeroMqSocket.send(bytes);
    }

}
