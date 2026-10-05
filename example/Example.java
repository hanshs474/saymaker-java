import io.github.hanshs474.saymaker.SayMaker;
import io.github.hanshs474.saymaker.SayMakerException;

/** SAYMAKER_API_KEY=sk-... java -cp saymaker-0.1.0.jar example/Example.java */
public class Example {
    public static void main(String[] args) {
        try {
            System.out.println(new SayMaker(System.getenv("SAYMAKER_API_KEY")).generate("a paper-cut layered mountain range at dusk"));
        } catch (SayMakerException e) {
            System.err.println(e.kind() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
