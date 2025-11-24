import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.Scanner;

public class AcademiaApp {    
    public static void main(String[] args) {
        try {
            Conexao conexaoMongo   = new Conexao();
            MongoDatabase database = conexaoMongo.getDatabase();
            
            MongoCollection<Document> beneficiosCollection   = database.getCollection("beneficios");
            MongoCollection<Document> planosCollection       = database.getCollection("planos");
            MongoCollection<Document> clientesCollection     = database.getCollection("clientes");
            MongoCollection<Document> funcionariosCollection = database.getCollection("funcionarios");
            MongoCollection<Document> telefonesCollection    = database.getCollection("telefones");
            MongoCollection<Document> cargosCollection       = database.getCollection("cargos");
            MongoCollection<Document> manutencoesCollection  = database.getCollection("manutencoes");
            MongoCollection<Document> equipamentosCollection = database.getCollection("equipamentos");

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\n=== SISTEMA ACADEMIA ===");
                System.out.println("1. Benefícios");
                System.out.println("2. Planos");
                System.out.println("3. Clientes");
                System.out.println("4. Funcionários");
                System.out.println("5. Telefones");
                System.out.println("6. Cargos");
                System.out.println("7. Manutenções");
                System.out.println("8. Equipamentos");
                System.out.println("9. Relatórios");
                System.out.println("0. Sair");
                System.out.print("Escolha uma opção: ");
                
                int opcao = scanner.nextInt();
                scanner.nextLine();
                
                switch (opcao) {
                    case 1 -> new BeneficioCRUD(beneficiosCollection, scanner).menu();
                    case 2 -> new PlanoCRUD(database, scanner).menu();
                    case 3 -> new ClienteCRUD(clientesCollection, telefonesCollection, planosCollection, scanner).menu();
                    case 4 -> new FuncionarioCRUD(database, scanner).menu();
                    case 5 -> new TelefoneCRUD(database, scanner).menu();
                    case 6 -> new CargoCRUD(cargosCollection, scanner).menu();
                    case 7 -> new ManutencaoCRUD(database, scanner).menu();
                    case 8 -> new EquipamentoCRUD(database, scanner).menu();
                    case 9 -> new Relatorios(database, scanner).menu();
                    case 0 -> { 
                        conexaoMongo.closeConnection();
                        return; 
                    }
                    default -> System.out.println("Opção inválida!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}