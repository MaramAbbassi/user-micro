package com.example.user;

import com.example.user.Exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

//import org.mindrot.jbcrypt.BCrypt;
//import com.example.utils.JwtUtils;

import org.eclipse.microprofile.rest.client.inject.RestClient;


@ApplicationScoped


public class UserService {

    @Inject
    EntityManager em;

    @Inject
    @RestClient
    PokemonServiceClient pokemonClient ;

    @Inject
    @RestClient
    EnchereServiceClient enchereClient;


    public List<User> getAllUsers() {
        return em.createQuery("SELECT u FROM User u", User.class).getResultList();
    }

    public User findUserById(Long id) {
        User user = em.find(User.class, id);
        if (user == null) {
            throw new UserNotFoundException("User with ID " + id + " not found.");
        }

        List<Pokemon> pokemons =user.getPokemons();
        List<Enchere> encheres = user.getEncheres();
        user.setPokemons(pokemons);
        user.setEncheres(encheres);

        return user;
    }

    @Transactional
    public void addUser(User user) {
        em.persist(user);
    }

    @Transactional
    public void updateUser(Long id, User updatedUser, String authenticatedRole) {
        if (!"Admin".equals(authenticatedRole)) {
            throw new SecurityException("Only Admins can update users.");
        }

        User existingUser = findUserById(id);
        if (existingUser == null) {
            throw new UserNotFoundException("Cannot update: User not found.");
        }

        if (updatedUser.getUsername() != null) {
            existingUser.setUsername(updatedUser.getUsername());
        }
        if (updatedUser.getEmail() != null) {
            existingUser.setEmail(updatedUser.getEmail());
        }
        if (updatedUser.getRole() != null) {
            existingUser.setRole(updatedUser.getRole());
        }

        em.merge(existingUser);
    }

    @Transactional
    public void deleteUser(Long id, String authenticatedRole) {
        if (!"Admin".equals(authenticatedRole)) {
            throw new SecurityException("Only Admins can delete users.");
        }

        User user = findUserById(id);
        if (user == null) {
            throw new UserNotFoundException("Cannot delete: User not found.");
        }

        em.remove(user);
    }



 /*   @Transactional
    public void registerUser(User user) {
        if (user.getUsername() == null || user.getEmail() == null || user.getPassword() == null) {
            throw new IllegalArgumentException("All fields are required.");
        }

        try {
            // Check if username or email already exists
            Long usernameCount = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", user.getUsername())
                    .getSingleResult();
            if (usernameCount > 0) {
                throw new IllegalArgumentException("Username already exists.");
            }

            Long emailCount = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", user.getEmail())
                    .getSingleResult();
            if (emailCount > 0) {
                throw new IllegalArgumentException("Email already exists.");
            }

            // Hash the password
            user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));

            // Set role to "User" if not provided
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("User");
            }

            // Default LimCoins for new users
            user.setLimCoins(1000);

            // Persist the user
            em.persist(user);
        } catch (Exception e) {
            // Add debugging logs
            e.printStackTrace();
            throw new RuntimeException("Error during user registration: " + e.getMessage());
        }
    }*/



   /* public String loginUser(String username, String password) {
        try {
            // Normalize input username by trimming spaces
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty.");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Password cannot be empty.");
            }


            // Query the database for the user by username
            User user = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            // Validate the password using BCrypt
            if (!BCrypt.checkpw(password, user.getPassword())) {
                throw new IllegalArgumentException("Invalid password.");
            }

            // Generate and return the JWT token
            return  JwtUtils.generateToken(user.getUsername(), user.getRole());

        } catch (NoResultException e) {
            throw new IllegalArgumentException("User not found with the provided username.");
        }
    }*/

    @Transactional
    public boolean addLimCoins(Long userId, int amount) {
        User user = findUserById(userId);
        if (user == null) {
            return false; // User not found
        }
        user.setLimCoins(user.getLimCoins() + amount);
        em.merge(user);
        return true; // Coins added successfully
    }


    @Transactional
    public boolean deductLimCoins(Long userId, int amount) {
        User user = findUserById(userId);
        if (user == null || user.getLimCoins() < amount) {
            return false; // User not found or insufficient coins
        }
        user.setLimCoins(user.getLimCoins() - amount);
        em.merge(user);
        return true; // Coins deducted successfully
    }

    private void checkForDuplicateUser(User user) {
        Long usernameCount = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.username = :username AND u.id != :id", Long.class)
                .setParameter("username", user.getUsername())
                .setParameter("id", user.getId())
                .getSingleResult();

        if (usernameCount > 0) {
            throw new IllegalArgumentException("Username already exists.");
        }

        Long emailCount = em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.email = :email AND u.id != :id", Long.class)
                .setParameter("email", user.getEmail())
                .setParameter("id", user.getId())
                .getSingleResult();

        if (emailCount > 0) {
            throw new IllegalArgumentException("Email already exists.");
        }
    }

    @Transactional
    public void addPokemonToUser(Long userId, Pokemon pokemon) {
        User user = findUserById(userId);
        user.getPokemons().add(pokemon);
        em.merge(user);
    }

    public List<Pokemon> getUserPokemons(Long userId) {
        return findUserById(userId).getPokemons();
    }



    public List<Enchere> getUserEncheres(Long userId) {
        return findUserById(userId).getEncheres();
    }

    @Transactional
    public String sellPokemonToSystem(Long userId, Long pokemonId) {
        // Find the user
        User user = findUserById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found.");
        }

        // Check if the user owns the Pokémon
        Pokemon pokemonToSell = user.getPokemons().stream()
                .filter(pokemon -> pokemon.getId().equals(pokemonId))
                .findFirst()
                .orElse(null);

        if (pokemonToSell == null) {
            throw new IllegalArgumentException("User does not own this Pokémon.");
        }

        double pokemonRealValue = pokemonToSell.getValeurReelle();

        user.setLimCoins(user.getLimCoins() +(int) pokemonRealValue);

        user.getPokemons().remove(pokemonToSell);

        em.merge(user);

        return "Pokémon sold successfully! Real value: " + pokemonRealValue + " LimCoins.";
    }

    public List<User> getTopUsersByLimCoins() {
        return em.createQuery("SELECT u FROM User u ORDER BY u.limCoins DESC", User.class)
                .setMaxResults(5) // Limit the results to 5
                .getResultList();
    }

    //get ecnhere by user id
    public List<Enchere> getEnchereByuserId(Long userId){
        User user=findUserById(userId);
        return user.getEncheres();
    }

    @Transactional
    public void placeBid(Long userId, Long enchereId,double amount) {
        User user = findUserById(userId);
        Enchere enchere=enchereClient.getEnchereById(enchereId);
        enchereClient.placerBid(userId, enchereId,amount); // Notify the Enchère microservice
        // Create a new bid and add it to the auction and user's active bids
        Bid bid = new Bid();
        bid.setUserId(userId);
        bid.setEnchere(enchere);
        bid.setAmount(amount);
        bid.setTimestamp(LocalDateTime.now());

        enchere.getBids().add(bid);
        user.getActiveBids().add(bid);
        user.getEncheres().add(enchere);
        enchere.setHighestBid(amount);
        enchere.setHighestBidderId(userId);

        // Persist changes
        em.merge(user);
        em.merge(enchere);

    }

    public double calculateTotalWonBids(Long userId) {
        User user = findUserById(userId);

        return user.getActiveBids().stream()
                .filter(bid -> bid.getEnchere().getStatus().equals("closed") &&
                        bid.getEnchere().getHighestBidderId().equals(userId))
                .mapToDouble(Bid::getAmount)
                .sum();
    }

    @Transactional
    public String abandonBids(Long userId, List<Long> bidIdsToAbandon) {
        User user = findUserById(userId);

        for (Long bidId : bidIdsToAbandon) {
            // Find the bid in the user's active bids
            Bid bid = user.getActiveBids().stream()
                    .filter(b -> b.getBIDid().equals(bidId))
                    .findFirst()
                    .orElse(null);

            if (bid != null) {
                Enchere enchere = bid.getEnchere();

                // Remove the bid from the auction
                enchere.getBids().remove(bid);
//lenna zid choufffffffffff
                // If this bid was the highest, reset the auction's highest bid
                if (enchere.getHighestBidderId().equals(userId)) {
                    enchere.setHighestBid(0);
                    enchere.setHighestBidderId(null);

                    // Optional: Update to the next highest bid
                    enchere.getBids().stream()
                            .max((b1, b2) -> Double.compare(b1.getAmount(), b2.getAmount()))
                            .ifPresent(nextHighestBid -> {
                                enchere.setHighestBid(nextHighestBid.getAmount());
                                enchere.setHighestBidderId(nextHighestBid.getUserId());
                            });
                }

                // Remove the bid from the user's active bids
                user.getActiveBids().remove(bid);

                // Persist changes
                em.merge(enchere);
            }
        }

        em.merge(user);
        return "Selected bids have been abandoned.";
    }

}
