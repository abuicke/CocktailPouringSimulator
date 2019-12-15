package lt.soe.cocktailmachine;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.util.Random;

public class CocktailPouringSimulator {

    private static final double AVERAGE_GRAMS_PER_MILLILITRE = 0.893F;
    /**
     * The volume of liquid which is poured by the peristaltic pump every second.
     * */
    private static final int FLOW_RATE_MILLILITRES_PER_SECOND = 20;
    /**
     * The average number of grams of liquid which is poured every second.
     * */
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

    /**
     * The current weight which is being read by the weight sensor.
     * */
    private static double currentSensorWeight = 0.0D;

    public static void main(String[] args) {
        // Initialise ZeroMQ context, this is ues to open sockets
        // e.g. context.createSocket(ZMQ.REP)
        try (ZContext context = new ZContext()) {
            // Put all simulation code in a loop, this is so the simulator will
            // reset after each time it processes a request from the server
            while (!Thread.currentThread().isInterrupted()) {
                // Create a ZeroMQ reply socket, this will wait
                // for a request from the cocktail machine server.
                ZMQ.Socket replySocket = context.createSocket(ZMQ.REP);
                // Bind the reply socket to port 5555.
                // This means the request socket on the server
                // should also be bound to port 5555.
                replySocket.bind("tcp://*:5555");
                System.out.println("reply socket bound to TCP port 5555, waiting for request from server...");
                // Wait for request from server
                byte[] request = replySocket.recv(0);

                // The first byte of the request is either 1 or 0
                // (i.e. true or false), which indicates if this
                // request is the first ingredient in the cocktail.
                boolean isFirstIngredient = request[0] == 0;
                // The remaining 4 bytes in the request amount to a single integer which
                // is the millilitres of the ingredient which needs to be poured.
                int ingredientQuantity = ByteBuffer.wrap(request).getInt(1);

                // If this is the first ingredient in the cocktail
                // then reset the weight sensor to zero.
                if (isFirstIngredient) {
                    currentSensorWeight = 0.0D;
                }

                System.out.println("isFirstIngredient = " + isFirstIngredient);
                System.out.println("ingredientQuantity = " + ingredientQuantity + "mls");

                // Send a reply back to the server to indicate that the
                // request has been received and has been processed.
                replySocket.send(new byte[]{0});
                // Close the socket, a new one will be opened when the
                // while loop starts over again from the top.
                replySocket.close();

                // Open a new ZeroMQ socket to send information back to the server.
                ZMQ.Socket publisherSocket = context.createSocket(ZMQ.PUB);
                // Use a different port, i.e. 5556 as opposed to 5555.
                publisherSocket.bind("tcp://*:5556");
                System.out.println("publisher socket bound to TCP port 5556, " +
                        "sending weight sensor readings to server...");

                // Wait 1 second to make sure that the server has
                // time to connect to the new socket opened on port 5556.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    throw new IllegalStateException(ie);
                }

                try {
                    // Put the glass on the weight sensor.
                    if (isFirstIngredient) {
                        // Wait between 1 and 5 seconds to place the glass on the sensor.
                        Thread.sleep(1000 + (1000 * new Random().nextInt(5)));
                        CocktailGlass cocktailGlass = getRandomCocktailGlass();
                        currentSensorWeight += cocktailGlass.weightGrams;
                        send(publisherSocket, currentSensorWeight);
                        System.out.println("empty glass has been placed on the " +
                                "weight sensor, weight of glass = " + currentSensorWeight + "g");
                    }
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                System.out.println("filling glass with " + ingredientQuantity + "mls of ingredient");
                double amountOfIngredientPouredWeight = 0;
                double filledIngredientWeight = ingredientQuantity * AVERAGE_GRAMS_PER_MILLILITRE;
                while (amountOfIngredientPouredWeight < filledIngredientWeight) {
                    // Get flow rate per tenth of a second.
                    amountOfIngredientPouredWeight += FLOW_RATE_GRAMS_PER_SECOND * 0.1;
                    System.out.println("poured " +
                            (amountOfIngredientPouredWeight / AVERAGE_GRAMS_PER_MILLILITRE) +
                            "mls of " + ingredientQuantity + "mls");
                    send(publisherSocket, currentSensorWeight + amountOfIngredientPouredWeight);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }

                currentSensorWeight += filledIngredientWeight;
                send(publisherSocket, -1);
                System.out.println("ingredient has been poured");
                publisherSocket.close();
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
